package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.model.CreateCharacterOutcome;
import taan.online.character.service.model.DeleteCharacterOutcome;
import taan.online.character.service.model.DeleteCharacterResult;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.persistence.DatabaseMigrator;

class CharacterDeletionIntegrationTest {
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
    void preservesArchivedDataFreesTheNameAndAllowsRestorationUnderAnotherName() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.FEMALE);
        var stranger = database.account("stranger@example.test", AccountGender.MALE);
        var original = database.create(owner.id(), "Luma", AccountGender.FEMALE).catalog().getFirst();
        long id = original.id().value();

        assertEquals(DeleteCharacterOutcome.NOT_FOUND, delete(stranger.id(), id).outcome());
        assertEquals(List.of(original), database.characters().findByAccount(owner.id()));

        var result = delete(owner.id(), id);

        assertEquals(DeleteCharacterOutcome.DELETED, result.outcome());
        assertTrue(result.catalog().isEmpty());
        assertTrue(database.characters().findByAccount(owner.id()).isEmpty());
        assertFalse(database.characters().existsByName(new CharacterName("Luma")));
        assertEquals(1, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
        assertEquals(4, database.scalar("SELECT count(*) FROM inventory_items"));
        long archivedAt = database.scalar("SELECT (extract(epoch FROM deleted_at) * 1000000)::bigint FROM characters");

        assertTrue(archivedAt > 0);
        assertEquals(DeleteCharacterOutcome.NOT_FOUND, delete(owner.id(), id).outcome());
        assertEquals(
            archivedAt,
            database.scalar("SELECT (extract(epoch FROM deleted_at) * 1000000)::bigint FROM characters")
        );

        var replacement = database.create(owner.id(), "Luma", AccountGender.FEMALE);

        assertEquals(CreateCharacterOutcome.CREATED, replacement.outcome());
        assertTrue(replacement.catalog().getFirst().id().value() > id);
        assertThrows(
            SQLException.class,
            () -> database.execute(
                "UPDATE characters SET deleted_at = NULL WHERE id = " + id
            )
        );

        database.execute("UPDATE characters SET name = 'RestoredHero', deleted_at = NULL WHERE id = " + id);
        var restored = database.characters().findByAccount(owner.id());

        assertEquals(2, restored.size());
        assertEquals(original.id(), restored.getFirst().id());
        assertEquals(original.accountId(), restored.getFirst().accountId());
        assertEquals(original.appearance(), restored.getFirst().appearance());
        assertEquals(original.level(), restored.getFirst().level());
        assertEquals(original.createdAt(), restored.getFirst().createdAt());
        assertEquals("RestoredHero", restored.getFirst().name().value());
    }

    @Test
    void rollsBackArchivalWhenTheRemainingCatalogIsCorruptOrTheTransactionIsNotCommitted() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        long target = database.seed(owner.id(), "MaleHero", CharacterFixtures.appearance(AccountGender.MALE));

        try (var transaction = database.characters().beginTransaction(owner.id())) {
            assertTrue(transaction.archive(new CharacterId(target)));
        }

        assertEquals(0, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));

        long broken = database.seed(owner.id(), "OtherHero", CharacterFixtures.appearance(AccountGender.MALE));
        database.execute(
            "UPDATE inventory_items SET slot = 16 WHERE slot = 8 AND inventory_id = "
                + "(SELECT id FROM inventories WHERE character_id = " + broken + " AND kind = 'EQUIPMENT')"
        );

        assertThrows(CharacterPersistenceException.class, () -> delete(owner.id(), target));
        assertEquals(0, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
        assertEquals(2, database.scalar("SELECT count(*) FROM characters"));
    }

    @Test
    void serializesRepeatedDeletionAndCreationAgainstTheSameAccount() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        long target = database.seed(owner.id(), "MaleHero", CharacterFixtures.appearance(AccountGender.MALE));
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return delete(owner.id(), target).outcome();
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return delete(owner.id(), target).outcome();
                }
            );
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(value -> value == DeleteCharacterOutcome.DELETED).count());
            assertEquals(1, outcomes.stream().filter(value -> value == DeleteCharacterOutcome.NOT_FOUND).count());
        }

        long next = database.seed(owner.id(), "NextHero", CharacterFixtures.appearance(AccountGender.MALE));

        try (var workers = Executors.newFixedThreadPool(2)) {
            var deleting = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return delete(owner.id(), next).outcome();
                }
            );
            var creating = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(owner.id(), "NewHero", AccountGender.MALE).outcome();
                }
            );

            assertEquals(DeleteCharacterOutcome.DELETED, deleting.get(10, TimeUnit.SECONDS));
            var outcome = creating.get(10, TimeUnit.SECONDS);

            assertTrue(
                outcome == CreateCharacterOutcome.CREATED || outcome == CreateCharacterOutcome.ACCOUNT_HAS_CHARACTERS
            );
            assertEquals(
                outcome == CreateCharacterOutcome.CREATED ? 1 : 0,
                database.characters().findByAccount(
                    owner.id()
                ).size()
            );
        }

        assertEquals(2, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
    }

    @Test
    void retainsAtomicNameUniquenessWhenTwoAccountsReuseAnArchivedName() throws Exception {
        var first = database.account("first@example.test", AccountGender.FEMALE);
        var second = database.account("second@example.test", AccountGender.MALE);
        long archived = database.seed(first.id(), "Luma", CharacterFixtures.appearance(AccountGender.FEMALE));

        assertEquals(DeleteCharacterOutcome.DELETED, delete(first.id(), archived).outcome());

        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(first.id(), "Luma", AccountGender.FEMALE).outcome();
                }
            );
            var two = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return database.create(second.id(), "Luma", AccountGender.MALE).outcome();
                }
            );
            var outcomes = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(value -> value == CreateCharacterOutcome.CREATED).count());
            assertEquals(1, outcomes.stream().filter(value -> value == CreateCharacterOutcome.NAME_TAKEN).count());
        }

        assertEquals(2, database.scalar("SELECT count(*) FROM characters WHERE name = 'Luma'"));
        assertEquals(1, database.scalar("SELECT count(*) FROM characters WHERE deleted_at IS NULL"));
    }

    @Test
    void upgradesExistingV2CharactersWithoutChangingTheirIdentityOrAppearance() throws Exception {
        database.execute("CREATE SCHEMA archive_upgrade");
        var isolated = new PGSimpleDataSource();
        isolated.setURL(database.source().getURL());
        isolated.setUser(database.source().getUser());
        isolated.setPassword(database.source().getPassword());
        isolated.setCurrentSchema("archive_upgrade");
        Flyway.configure().dataSource(isolated).defaultSchema("archive_upgrade").target("2").load().migrate();

        var owner = database.account("migration@example.test", AccountGender.FEMALE);

        try (var connection = isolated.getConnection(); var statement = connection.createStatement()) {
            statement.execute(
                "INSERT INTO accounts (login, credential_verifier, gender) "
                        + "SELECT login, credential_verifier, gender FROM public.accounts"
            );
            statement.execute("INSERT INTO characters (account_id, name) VALUES (" + owner.id().value() + ", 'Luma')");
            statement.execute(
                "INSERT INTO character_appearance_parts "
                        + "SELECT 1, slot, 0, 0, 0, 0 FROM generate_series(0, 15) slot"
            );
        }

        new DatabaseMigrator().migrate(isolated);
        new DatabaseMigrator().migrate(isolated);
        var repository = new JdbcCharacterRepository(isolated, 5, new JdbcInventoryRepository(isolated, 5));
        var loaded = repository.findByAccount(owner.id());

        assertEquals(1, loaded.size());
        assertEquals(1, loaded.getFirst().id().value());
        assertEquals("Luma", loaded.getFirst().name().value());
        assertEquals(16, loaded.getFirst().appearance().parts().size());
        assertTrue(repository.existsByName(new CharacterName("Luma")));

        try (var transaction = repository.beginTransaction(owner.id())) {
            assertTrue(transaction.archive(loaded.getFirst().id()));
            transaction.commit();
        }

        assertTrue(repository.findByAccount(owner.id()).isEmpty());
    }

    private DeleteCharacterResult delete(AccountId owner, long id) {
        return new DeleteCharacterAction(ignored -> Optional.of(owner), database.characters()).execute(
            UUID.randomUUID(),
            id
        );
    }
}
