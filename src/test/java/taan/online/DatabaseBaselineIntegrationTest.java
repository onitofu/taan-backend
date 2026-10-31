package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.account.model.AccountId;
import taan.online.inventory.model.InventoryKind;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.persistence.DatabaseMigrator;

class DatabaseBaselineIntegrationTest {
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

    @Test
    void installsTheFinalSchemaAndReadableCatalogsExactlyOnceWithoutCreatingPlayers() throws Exception {
        var source = source("catalog_baseline");
        var migrator = new DatabaseMigrator();
        migrator.migrate(source);
        migrator.migrate(source);

        assertEquals(5, scalar(source, "SELECT count(*) FROM flyway_schema_history WHERE success"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM accounts"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM characters"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM match_results"));
        assertEquals(610, scalar(source, "SELECT count(*) FROM item_definitions"));
        assertEquals(223, scalar(source, "SELECT count(*) FROM palettes"));
        assertEquals(162, scalar(source, "SELECT count(*) FROM palettes WHERE kind = 'ITEM'"));
        assertEquals(59, scalar(source, "SELECT count(*) FROM palettes WHERE kind = 'SKIN'"));
        assertEquals(2, scalar(source, "SELECT count(*) FROM palettes WHERE kind = 'FALLBACK'"));
        assertEquals("Default", text(source, "SELECT name FROM palettes WHERE id = 0"));
        assertEquals("Legacy fallback (283)", text(source, "SELECT name FROM palettes WHERE id = 283"));
        assertEquals("Thistle", text(source, "SELECT name FROM palettes WHERE id = 309"));
        assertEquals("Ghost Costume", text(source, "SELECT name FROM item_definitions WHERE item_id = 574"));
        assertEquals("CY Hairstyle", text(source, "SELECT name FROM item_definitions WHERE item_id = 1044"));
        assertEquals("Red Vespa", text(source, "SELECT name FROM item_definitions WHERE item_id = 1235"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM item_definitions WHERE name !~ '^[ -~]+$'"));
        assertEquals(1, scalar(source, "SELECT count(*) FROM item_definitions WHERE NOT resource_available"));
        assertEquals(0, scalar(source, """
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema = 'catalog_baseline' AND table_name = 'character_appearance_parts'
            """));
        assertEquals("BEGINNER,ROOKIE,VETERAN,FREE", text(
            source,
            "SELECT string_agg(name, ',' ORDER BY id) FROM channel_types"
        ));
        assertTrue(scalar(source, "INSERT INTO channel_groups (name) VALUES ('Extra') RETURNING id") > 1);
    }

    @Test
    void enforcesEveryPaletteAndChannelTypeReferenceAndRetainsStoredTuplesAcrossFreshConnections() throws Exception {
        var source = source("reference_baseline");
        new DatabaseMigrator().migrate(source);
        execute(source, """
            INSERT INTO accounts (login, credential_verifier, gender) VALUES ('fixture', 'public-verifier', 1)
            """);
        execute(source, "INSERT INTO characters (account_id, name) VALUES (1, 'PaletteHero')");
        execute(source, """
            INSERT INTO inventories (character_id, kind) VALUES (1, 'BAG'), (1, 'EQUIPMENT'), (1, 'JEWEL_BOX')
            """);
        execute(source, """
            INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, palette_2_id, palette_3_id)
            VALUES (1, 0, 688, 0, 283, 537)
            """);

        for (String table : List.of("item_definitions", "inventory_items")) {
            String prefix = table.equals("item_definitions") ? "default_" : "";

            for (int index = 1; index <= 3; index++) {
                String column = prefix + "palette_" + index + "_id";
                var failure = assertThrows(
                    SQLException.class,
                    () -> execute(source, "UPDATE " + table + " SET " + column + " = 65535")
                );
                assertEquals("23503", failure.getSQLState());
                assertThrows(
                    SQLException.class,
                    () -> execute(source, "UPDATE " + table + " SET " + column + " = NULL")
                );
            }
        }

        for (String sql : List.of(
            "DELETE FROM palettes WHERE id = 0",
            "DELETE FROM palettes WHERE id = 283",
            "DELETE FROM channel_types WHERE id = 0",
            "UPDATE channels SET kind = 2 WHERE id = 1"
        )) {
            String expectedState = sql.startsWith("DELETE") ? "23001" : "23503";
            assertEquals(expectedState, assertThrows(SQLException.class, () -> execute(source, sql)).getSQLState());
        }

        execute(source, "UPDATE channel_groups SET type = 2 WHERE id = 1");
        new DatabaseMigrator().migrate(source);
        assertEquals(2, scalar(source, "SELECT type FROM channel_groups WHERE id = 1"));
        assertEquals(0, scalar(source, "SELECT kind FROM channels WHERE id = 1"));

        try (var transaction = new JdbcInventoryRepository(source, 5).beginTransaction(new AccountId(1), 1)) {
            var bag = transaction.state().inventories().stream().filter(i -> i.kind() == InventoryKind.BAG)
                .findFirst().orElseThrow();
            var item = bag.items().getFirst();
            assertEquals(0, item.palette1Id());
            assertEquals(283, item.palette2Id());
            assertEquals(537, item.palette3Id());
            transaction.commit();
        }
    }

    @Test
    void rollsBackPartialSchemaCreationAndResumesAfterTheConflictIsRemovedWithoutLoggingSecrets() throws Exception {
        var source = source("rollback_baseline");
        Flyway.configure().dataSource(source).target("2").load().migrate();
        execute(source, "CREATE TABLE marker (id integer)");
        execute(source, "CREATE INDEX characters_account_id_idx ON marker(id)");

        try (var logs = new LogCapture()) {
            assertThrows(RuntimeException.class, () -> new DatabaseMigrator().migrate(source));
            assertEquals(2, scalar(source, "SELECT count(*) FROM flyway_schema_history WHERE success"));
            assertEquals(0, scalar(source, """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'rollback_baseline' AND table_name IN ('characters', 'inventories')
                """));
            assertFalse(logs.text().contains(database.source().getPassword()));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }

        execute(source, "DROP INDEX characters_account_id_idx");
        new DatabaseMigrator().migrate(source);
        assertEquals(5, scalar(source, "SELECT count(*) FROM flyway_schema_history WHERE success"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM characters"));
        assertEquals(0, scalar(source, "SELECT count(*) FROM marker"));
    }

    private PGSimpleDataSource source(String schema) throws SQLException {
        database.execute("CREATE SCHEMA " + schema);
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema(schema);
        return source;
    }

    private void execute(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalar(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private String text(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }
}
