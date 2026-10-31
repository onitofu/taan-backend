package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.CharacterFixtures;
import taan.online.CharacterTestDatabase;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.character.model.CharacterName;
import taan.online.character.service.model.CreateCharacterOutcome;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.persistence.DatabaseMigrator;

class CharacterPersistenceIntegrationTest {
    private static CharacterTestDatabase database;

    @BeforeAll
    static void start() {
        database = new CharacterTestDatabase();
    }

    @AfterAll
    static void stop() {
        if (database != null) {
            database.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        database.reset();
    }

    @Test
    void completesTheBaselineWithoutChangingAccountsAndReloadsCommittedAppearance() throws Exception {
        database.execute("CREATE SCHEMA character_upgrade");
        var isolated = new PGSimpleDataSource();
        isolated.setURL(database.source().getURL());
        isolated.setUser(database.source().getUser());
        isolated.setPassword(database.source().getPassword());
        isolated.setCurrentSchema("character_upgrade");
        Flyway.configure().dataSource(isolated).defaultSchema("character_upgrade").target("1").load().migrate();
        var accounts = new JdbcAccountRepository(isolated, 5);
        var original = accounts.tryCreate(
            new LoginName("migration@example.test"),
            new CredentialVerifier(TestFixtures.VERIFIER),
            AccountGender.FEMALE
        ).orElseThrow();

        new DatabaseMigrator().migrate(isolated);
        new DatabaseMigrator().migrate(isolated);

        assertEquals(original, accounts.findByLogin(original.login()).orElseThrow());
        var migrated = new JdbcCharacterRepository(isolated, 5, new JdbcInventoryRepository(isolated, 5));

        assertTrue(migrated.findByAccount(original.id()).isEmpty());

        var owner = database.account("fresh@example.test", AccountGender.FEMALE);
        var result = database.create(owner.id(), "Luma", AccountGender.FEMALE);
        var reloaded = database.characters().findByAccount(owner.id());

        assertEquals(CreateCharacterOutcome.CREATED, result.outcome());
        assertEquals(result.catalog(), reloaded);
        assertEquals(CharacterFixtures.appearance(AccountGender.FEMALE), reloaded.getFirst().appearance());
        assertEquals(1, reloaded.getFirst().level());
        assertEquals(4, database.scalar("SELECT count(*) FROM inventory_items"));
        assertTrue(database.characters().existsByName(new CharacterName("Luma")));
        assertFalse(database.characters().existsByName(new CharacterName("luma")));
    }

    @Test
    void serializesConcurrentCreationsOfOneAccountWithoutACharacterCountConstraint() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(owner.id(), "MaleHero", AccountGender.MALE).outcome();
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(owner.id(), "Luma", AccountGender.MALE).outcome();
                }
            );
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(value -> value == CreateCharacterOutcome.CREATED).count());
            assertEquals(
                1,
                outcomes.stream().filter(value -> value == CreateCharacterOutcome.ACCOUNT_HAS_CHARACTERS).count()
            );
        }

        assertEquals(1, database.characters().findByAccount(owner.id()).size());

        for (int index = 0; index < 9; index++) {
            database.seed(owner.id(), "SeedHero" + index, CharacterFixtures.appearance(AccountGender.MALE));
        }

        var catalog = database.characters().findByAccount(owner.id());

        assertEquals(10, catalog.size());
        assertEquals(40, database.scalar("SELECT count(*) FROM inventory_items"));
        assertTrue(catalog.getFirst().id().value() < catalog.getLast().id().value());
        assertEquals(
            CreateCharacterOutcome.ACCOUNT_HAS_CHARACTERS,
            database.create(owner.id(), "OtherHero", AccountGender.MALE).outcome()
        );
    }

    @Test
    void enforcesGlobalCaseSensitiveNamesAcrossConcurrentOwners() throws Exception {
        var firstOwner = database.account("one@example.test", AccountGender.FEMALE);
        var secondOwner = database.account("two@example.test", AccountGender.FEMALE);
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(firstOwner.id(), "Luma", AccountGender.FEMALE).outcome();
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(secondOwner.id(), "Luma", AccountGender.FEMALE).outcome();
                }
            );
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(value -> value == CreateCharacterOutcome.CREATED).count());
            assertEquals(1, outcomes.stream().filter(value -> value == CreateCharacterOutcome.NAME_TAKEN).count());
        }

        var third = database.account("three@example.test", AccountGender.FEMALE);

        assertEquals(
            CreateCharacterOutcome.CREATED,
            database.create(third.id(), "luma", AccountGender.FEMALE).outcome()
        );
        assertEquals(2, database.scalar("SELECT count(*) FROM characters"));
    }

    @Test
    void rollsBackPartialBatchWritesAndRejectsMissingEquipmentInventories() throws Exception {
        var owner = database.account("rollback@example.test", AccountGender.MALE);
        database.execute("ALTER TABLE inventory_items ADD CONSTRAINT private_marker CHECK (slot <> 8)");

        try {
            var failure = assertThrows(
                CharacterPersistenceException.class,
                () -> database.create(owner.id(), "MaleHero", AccountGender.MALE)
            );

            assertFalse(failure.getMessage().contains("MaleHero"));
            assertFalse(failure.getCause().getMessage().contains("private_marker"));
            assertEquals(0, database.scalar("SELECT count(*) FROM characters"));
            assertEquals(0, database.scalar("SELECT count(*) FROM inventory_items"));
        } finally {
            database.execute("ALTER TABLE inventory_items DROP CONSTRAINT private_marker");
        }

        database.create(owner.id(), "MaleHero", AccountGender.MALE);
        database.execute("DELETE FROM inventory_items");

        var empty = database.characters().findByAccount(owner.id()).getFirst().appearance();

        assertEquals(16, empty.parts().size());
        assertTrue(empty.parts().stream().allMatch(part -> part.itemId() == 0));

        database.execute("DELETE FROM inventories WHERE kind = 'EQUIPMENT'");

        assertThrows(CharacterPersistenceException.class, () -> database.characters().findByAccount(owner.id()));
        assertThrows(
            CharacterPersistenceException.class,
            () -> database.characters().beginTransaction(new AccountId(999))
        );
    }

    @Test
    void appliesDatabaseNameAndNumericConstraintsWhilePreservingAllowedUnicodeAndPunctuation() throws Exception {
        var owner = database.account("constraints@example.test", AccountGender.MALE);

        for (String name : List.of("A_[]", "ÁắỸz", "a".repeat(18))) {
            database.seed(owner.id(), name, CharacterFixtures.appearance(AccountGender.MALE));

            assertTrue(database.characters().existsByName(new CharacterName(name)));
        }

        for (String name : List.of("abc", "a".repeat(19), "bad name", "Тест", "A😀z", "ab\0c")) {
            assertThrows(
                SQLException.class,
                () -> database.seed(owner.id(), name, CharacterFixtures.appearance(AccountGender.MALE))
            );
        }

        for (String sql : List.of(
            "UPDATE characters SET level = 0",
            "UPDATE characters SET level = 65536",
            "UPDATE inventory_items SET slot = 256",
            "UPDATE inventory_items SET item_id = -1",
            "UPDATE inventory_items SET item_id = 4294967296",
            "UPDATE inventory_items SET palette_1_id = -1",
            "UPDATE inventory_items SET palette_2_id = 65536",
            "UPDATE inventory_items SET palette_3_id = -1",
            "DELETE FROM accounts",
            "DELETE FROM characters"
        )) {
            assertThrows(SQLException.class, () -> database.execute(sql));
        }

        assertEquals(3, database.characters().findByAccount(owner.id()).size());
    }
}
