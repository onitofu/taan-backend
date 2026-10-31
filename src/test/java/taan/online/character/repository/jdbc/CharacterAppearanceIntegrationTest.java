package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.CharacterTestDatabase;
import taan.online.account.model.AccountGender;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterId;

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
            "UPDATE inventory_items SET item_id = 806, palette_1_id = 303, palette_2_id = 261, palette_3_id = 537 "
                + "WHERE id = " + uid
        );
        var reloaded = database.characters().findByAccount(owner.id()).getFirst();

        assertEquals(new AppearancePart(1, 806, 303, 261, 537), reloaded.appearance().parts().get(1));
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

    private long scalar(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
