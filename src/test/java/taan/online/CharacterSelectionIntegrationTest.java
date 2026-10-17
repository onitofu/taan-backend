package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.character.repository.jdbc.CharacterPersistenceException;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.model.DeleteCharacterOutcome;
import taan.online.persistence.DatabaseMigrator;

class CharacterSelectionIntegrationTest {
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
    void retainsStarterUidsAndAppearanceAcrossSelectionReconnectAndRestartForBothGenders() throws Exception {
        for (var gender : AccountGender.values()) {
            var owner = database.account(gender.name().toLowerCase() + "@example.test", gender);
            String name = gender == AccountGender.MALE ? "MaleHero" : "Luma";
            var created = database.create(owner.id(), name, gender).catalog().getFirst();
            long id = created.id().value();
            var config = database.configuration();
            byte[] selected;

            try (var application = new Application(config)) {
                application.start();

                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    client.sendTogether(CharacterFixtures.select(id), SelectionFixtures.channels());
                    selected = client.read();

                    assertEquals(4, SelectionFixtures.assertSelection(selected, id, gender).size());
                    SelectionFixtures.assertPlayableTime(client.read());
                    SelectionFixtures.assertChannels(client.read(), config.port());

                    client.sendTogether(SelectionFixtures.logging(id), SelectionFixtures.cash(0xffff_ffffL, 0));

                    assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());

                    client.sendTogether(Arrays.copyOf(CharacterFixtures.select(id), 4), SelectionFixtures.channels());

                    SelectionFixtures.assertChannels(client.read(), config.port());

                    client.disconnect();
                }

                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    client.sendFragmented(CharacterFixtures.select(id));

                    assertArrayEquals(selected, client.read());
                    SelectionFixtures.assertPlayableTime(client.read());

                    client.disconnect();
                }
            }

            try (var application = new Application(config)) {
                application.start();

                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    client.send(CharacterFixtures.select(id));

                    assertArrayEquals(selected, client.read());
                    SelectionFixtures.assertPlayableTime(client.read());

                    client.disconnect();
                }
            }
        }

        assertEquals(6, database.scalar("SELECT count(*) FROM inventories"));
        assertEquals(8, database.scalar("SELECT count(*) FROM inventory_items"));
    }

    @Test
    void rejectsForeignArchivedAndInvalidSelectionsWithoutLeakingClaimsOrSendingSuccess() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.FEMALE);
        var other = database.account("other@example.test", AccountGender.MALE);
        long own = database.create(owner.id(), "Luma", AccountGender.FEMALE).catalog().getFirst().id().value();
        long foreign = database.create(other.id(), "MaleHero", AccountGender.MALE).catalog().getFirst().id().value();
        long archived = database.seed(owner.id(), "OldHero", CharacterFixtures.appearance(AccountGender.FEMALE));

        assertEquals(
            DeleteCharacterOutcome.DELETED,
            new DeleteCharacterAction(
                ignored -> Optional.of(owner.id()),
                database.characters()
            ).execute(UUID.randomUUID(), archived).outcome()
        );

        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            for (long id : new long[]{foreign, archived, 0, 0xffff_ffffL}) {
                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    client.send(CharacterFixtures.select(id));
                    client.awaitClosure();
                }
            }

            try (var client = new CharacterTestClient(config.port())) {
                client.login(owner.login().value());
                client.send(CharacterFixtures.select(own));

                SelectionFixtures.assertSelection(client.read(), own, AccountGender.FEMALE);
                SelectionFixtures.assertPlayableTime(client.read());

                client.send(CharacterFixtures.delete(own, 0));
                client.awaitClosure();
            }

            try (var client = new CharacterTestClient(config.port())) {
                client.login(owner.login().value());
                client.send(SelectionFixtures.channels());
                client.awaitClosure();
            }

            for (int count : new int[]{0, 2, -1}) {
                try (var client = new CharacterTestClient(config.port())) {
                    client.login(owner.login().value());
                    byte[] root = CharacterFixtures.select(own);
                    ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).putInt(4, count);
                    client.send(root);
                    client.awaitClosure();
                }
            }
        }

        assertEquals(1, database.characters().findByAccount(owner.id()).size());
        assertEquals(12, database.scalar("SELECT count(*) FROM inventory_items"));
    }

    @Test
    void rollsBackAllCharacterDataIfStarterInsertionFailsAndRefusesMissingInventories() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.FEMALE);
        database.execute("ALTER TABLE inventory_items ADD CONSTRAINT reject_starter CHECK (slot <> 8)");

        assertThrows(
            CharacterPersistenceException.class,
            () -> database.create(
                owner.id(),
                "Luma",
                AccountGender.FEMALE
            )
        );
        assertEquals(0, database.scalar("SELECT count(*) FROM characters"));
        assertEquals(0, database.scalar("SELECT count(*) FROM inventories"));
        assertEquals(0, database.scalar("SELECT count(*) FROM inventory_items"));

        database.execute("ALTER TABLE inventory_items DROP CONSTRAINT reject_starter");
        long id = database.create(owner.id(), "Luma", AccountGender.FEMALE).catalog().getFirst().id().value();
        database.execute("DELETE FROM inventories WHERE kind = 'BAG'");
        var config = database.configuration();

        try (var application = new Application(config); var logs = new LogCapture()) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                client.login(owner.login().value());
                client.send(CharacterFixtures.select(id));
                client.awaitClosure();
            }

            assertTrue(logs.text().contains("character.selection.failed"));
            assertTrue(!logs.text().contains("character.selection.succeeded"));
        }
    }

    @Test
    void backfillsBothActiveAndArchivedV3CharactersExactlyOnceAndRollsBackInvalidMigration() throws Exception {
        for (boolean complete : new boolean[]{true, false}) {
            String schema = complete ? "inventory_upgrade" : "inventory_invalid";
            database.execute("CREATE SCHEMA " + schema);
            var source = new PGSimpleDataSource();
            source.setURL(database.source().getURL());
            source.setUser(database.source().getUser());
            source.setPassword(database.source().getPassword());
            source.setCurrentSchema(schema);
            Flyway.configure().dataSource(source).defaultSchema(schema).target("3").load().migrate();

            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute(
                    "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('upgrade', 'public-verifier', 1)"
                );
                statement.execute("INSERT INTO characters (account_id, name) VALUES (1, 'MaleHero'), (1, 'OldHero')");
                statement.execute("UPDATE characters SET deleted_at = CURRENT_TIMESTAMP WHERE name = 'OldHero'");
                statement.execute(
                    "INSERT INTO character_appearance_parts "
                        + "SELECT c.id, slot, CASE WHEN slot IN (1, 3, 5, 8) THEN 100 + slot ELSE 0 END, "
                        + "CASE WHEN slot IN (1, 3, 5, 8) THEN 1 ELSE 0 END, "
                        + "CASE WHEN slot IN (1, 3, 5, 8) THEN 2 ELSE 0 END, "
                        + "CASE WHEN slot IN (1, 3, 5, 8) THEN 3 ELSE 0 END "
                        + "FROM characters c CROSS JOIN generate_series(0, " + (complete ? 15 : 14) + ") slot"
                );
            }

            if (complete) {
                new DatabaseMigrator().migrate(source);
                new DatabaseMigrator().migrate(source);

                try (var connection = source.getConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery(
                        "SELECT count(*), count(DISTINCT inventory_id) FROM inventory_items"
                    )) {
                    rows.next();

                    assertEquals(8, rows.getInt(1));
                    assertEquals(2, rows.getInt(2));
                }
            } else {
                assertThrows(RuntimeException.class, () -> new DatabaseMigrator().migrate(source));

                try (var connection = source.getConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT to_regclass('inventory_invalid.inventories')")) {
                    rows.next();

                    assertTrue(rows.getObject(1) == null);
                }
            }
        }
    }
}
