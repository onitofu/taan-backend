package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.CharacterTestDatabase;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterId;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.persistence.DatabaseMigrator;

class CharacterAppearanceIntegrationTest {
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
    void derivesCatalogFromCurrentEquipmentAndRetainsItemsWhenMovedOrArchived() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.FEMALE);
        var created = database.create(owner.id(), "Luma", AccountGender.FEMALE).catalog().getFirst();
        long id = created.id().value();
        long uid = database.scalar("SELECT id FROM inventory_items WHERE slot = 1");
        database.execute(
            "UPDATE inventory_items SET item_id = 806, palette_1_id = 12, palette_2_id = 34, palette_3_id = 56 "
                + "WHERE id = " + uid
        );
        var reloaded = database.characters().findByAccount(owner.id()).getFirst();

        assertEquals(new AppearancePart(1, 806, 12, 34, 56), reloaded.appearance().parts().get(1));
        assertEquals(created.id(), reloaded.id());
        assertEquals(created.createdAt(), reloaded.createdAt());
        assertEquals(created.appearance().parts().get(3), reloaded.appearance().parts().get(3));
        assertEquals(4, database.scalar("SELECT count(*) FROM inventory_items"));
        assertEquals(
            0,
            scalar(
                database.source(),
                "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name = 'character_appearance_parts'"
            )
        );

        database.execute(
            "UPDATE inventory_items SET inventory_id = (SELECT id FROM inventories WHERE character_id = "
                + id + " AND kind = 'BAG'), slot = 42 WHERE id = " + uid
        );
        var unequipped = database.characters().findByAccount(owner.id()).getFirst();

        assertEquals(new AppearancePart(1, 0, 0, 0, 0), unequipped.appearance().parts().get(1));
        assertEquals(created.appearance().parts().get(3), unequipped.appearance().parts().get(3));

        try (var transaction = database.characters().beginTransaction(owner.id())) {
            var bag = transaction.inventories(new CharacterId(id)).stream()
                .filter(inventory -> inventory.kind().name().equals("BAG")).findFirst().orElseThrow();

            assertEquals(uid, bag.items().getFirst().id().value());
            assertEquals(806L, bag.items().getFirst().itemId());
            assertEquals(unequipped, transaction.catalog().getFirst());
            assertTrue(transaction.archive(new CharacterId(id)));

            transaction.commit();
        }

        assertTrue(database.characters().findByAccount(owner.id()).isEmpty());
        assertEquals(4, database.scalar("SELECT count(*) FROM inventory_items"));

        database.execute("UPDATE characters SET deleted_at = NULL WHERE id = " + id);

        assertEquals(unequipped, database.characters().findByAccount(owner.id()).getFirst());
    }

    @Test
    void removesMatchingCopiesForActiveArchivedAndEmptyCharactersWithoutChangingInventoryIdentities() throws Exception {
        var source = legacy("appearance_upgrade");
        long uid = scalar(source, "SELECT max(id) FROM inventory_items");
        long inventoryId = scalar(source, "SELECT max(id) FROM inventories");
        String before = inventoryRows(source, true);

        new DatabaseMigrator().migrate(source);
        new DatabaseMigrator().migrate(source);

        assertEquals(before, inventoryRows(source, false));
        var original = repository(source).findByAccount(new AccountId(1));

        assertEquals(2, original.size());
        assertEquals(original, repository(source).findByAccount(new AccountId(1)));
        assertEquals(uid, scalar(source, "SELECT max(id) FROM inventory_items"));
        assertEquals(inventoryId, scalar(source, "SELECT max(id) FROM inventories"));
        assertEquals(8, scalar(source, "SELECT count(*) FROM inventory_items"));
        assertEquals(9, scalar(source, "SELECT count(*) FROM inventories"));
        assertEquals(1, scalar(source, "SELECT count(*) FROM characters WHERE deleted_at IS NOT NULL"));
        assertEquals(
            0,
            scalar(
                source,
                "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'appearance_upgrade' AND table_name = 'character_appearance_parts'"
            )
        );
        assertTrue(original.getLast().appearance().parts().stream().allMatch(part -> part.itemId() == 0));

        execute(source, "UPDATE characters SET deleted_at = NULL WHERE id = 2");
        var restored = repository(source).findByAccount(new AccountId(1));

        assertEquals(3, restored.size());
        assertEquals(original.getFirst().appearance(), restored.get(1).appearance());
    }

    @Test
    void rejectsDivergentIncompleteAndUnrepresentableCopiesWithoutDroppingAnyData() throws Exception {
        var corruptions = List.of(
            "UPDATE character_appearance_parts SET item_id = 999 WHERE character_id = 1 AND slot = 1",
            "UPDATE character_appearance_parts SET parameter0 = 999 WHERE character_id = 1 AND slot = 1",
            "UPDATE character_appearance_parts SET parameter1 = 999 WHERE character_id = 1 AND slot = 1",
            "UPDATE character_appearance_parts SET parameter2 = 999 WHERE character_id = 1 AND slot = 1",
            "UPDATE character_appearance_parts SET parameter0 = 1 WHERE character_id = 1 AND slot = 0",
            "DELETE FROM character_appearance_parts WHERE character_id = 1 AND slot = 15",
            "DELETE FROM inventory_items WHERE slot = 1",
            "UPDATE inventory_items SET slot = 16 WHERE slot = 1",
            "INSERT INTO inventory_items (inventory_id, slot, item_id, parameter0, parameter1, parameter2) "
                + "SELECT id, 16, 999, 0, 0, 0 FROM inventories WHERE character_id = 1 AND kind = 'EQUIPMENT'",
            "UPDATE inventory_items SET item_id = 999 WHERE inventory_id = "
                + "(SELECT id FROM inventories WHERE character_id = 2 AND kind = 'EQUIPMENT')",
            "DELETE FROM inventories WHERE character_id = 3 AND kind = 'EQUIPMENT'"
        );

        for (int index = 0; index < corruptions.size(); index++) {
            String schema = "appearance_invalid_" + index;
            var source = legacy(schema);
            execute(source, corruptions.get(index));
            long items = scalar(source, "SELECT count(*) FROM inventory_items");
            long parts = scalar(source, "SELECT count(*) FROM character_appearance_parts");
            var failure = assertThrows(RuntimeException.class, () -> new DatabaseMigrator().migrate(source));

            assertTrue(failure.getMessage().contains("Character appearance and equipment disagree"));
            assertFalse(failure.getMessage().contains("SecretHero"));
            assertEquals(items, scalar(source, "SELECT count(*) FROM inventory_items"));
            assertEquals(parts, scalar(source, "SELECT count(*) FROM character_appearance_parts"));
            assertEquals(3, scalar(source, "SELECT count(*) FROM characters"));
            assertEquals(4, scalar(source, "SELECT max(version::integer) FROM flyway_schema_history"));
        }
    }

    private PGSimpleDataSource legacy(String schema) throws SQLException {
        database.execute("CREATE SCHEMA " + schema);
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema(schema);
        Flyway.configure().dataSource(source).defaultSchema(schema).target("3").load().migrate();
        execute(
            source,
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('upgrade', 'public-verifier', 2)"
        );
        execute(
            source,
            "INSERT INTO characters (account_id, name) VALUES (1, 'SecretHero'), (1, 'OldHero'), (1, 'BareHero')"
        );
        execute(source, "UPDATE characters SET deleted_at = CURRENT_TIMESTAMP WHERE id = 2");
        execute(
            source,
            "INSERT INTO character_appearance_parts "
                + "SELECT c.id, slot, CASE WHEN c.id < 3 AND slot IN (1, 3, 5, 8) THEN CASE "
                    + "slot WHEN 1 THEN 731 WHEN 3 THEN 824 WHEN 5 THEN 727 ELSE 737 END ELSE 0 END, "
                + "CASE WHEN c.id < 3 AND slot IN (1, 3, 5, 8) THEN 12 ELSE 0 END, "
                + "CASE WHEN c.id < 3 AND slot IN (1, 3, 5, 8) THEN 34 ELSE 0 END, "
                + "CASE WHEN c.id < 3 AND slot IN (1, 3, 5, 8) THEN 56 ELSE 0 END "
                + "FROM characters c CROSS JOIN generate_series(0, 15) slot"
        );
        Flyway.configure().dataSource(source).defaultSchema(schema).target("4").load().migrate();
        return source;
    }

    private String inventoryRows(PGSimpleDataSource source, boolean legacy) throws SQLException {
        String sql = "SELECT string_agg(concat_ws(':', id, inventory_id, slot, item_id, quantity, "
            + (legacy ? "parameter0, parameter1, parameter2" : "palette_1_id, palette_2_id, palette_3_id")
            + "), ',' ORDER BY id) FROM inventory_items";

        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private JdbcCharacterRepository repository(PGSimpleDataSource source) {
        return new JdbcCharacterRepository(source, 5, new JdbcInventoryRepository(source, 5));
    }

    private void execute(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalar(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
