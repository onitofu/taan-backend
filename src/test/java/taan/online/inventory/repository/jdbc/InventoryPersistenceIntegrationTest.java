package taan.online.inventory.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.CharacterTestDatabase;
import taan.online.InventoryFixtures;
import taan.online.account.model.AccountGender;
import taan.online.inventory.model.InventoryId;
import taan.online.inventory.model.InventoryItemId;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.model.ItemCatalog;
import taan.online.inventory.model.ItemPlacement;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.inventory.service.TestInventoryMoveContextAccess;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.MoveInventoryItemCommand;

class InventoryPersistenceIntegrationTest {
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
    void loadsAllDefinitionsAndKeepsTheStartupSnapshotIndependentOfLaterDatabaseEdits() throws Exception {
        var repository = new JdbcItemDefinitionRepository(database.source(), 5);
        var definitions = repository.load();
        var catalog = new ItemCatalog();
        catalog.initialize(definitions);

        assertEquals(610, definitions.size());
        assertEquals(590, definitions.stream().filter(value -> value.equipmentSlot() != null).count());
        assertFalse(catalog.available(636));
        assertTrue(catalog.fits(1039, 1, AccountGender.MALE));
        assertThrows(UnsupportedOperationException.class, definitions::clear);

        try {
            database.execute("UPDATE item_definitions SET gender = 2, default_palette_1_id = 303 WHERE item_id = 1039");
            var changed = new ItemCatalog();
            changed.initialize(repository.load());

            assertTrue(catalog.fits(1039, 1, AccountGender.MALE));
            assertFalse(changed.fits(1039, 1, AccountGender.MALE));
            assertTrue(changed.fits(1039, 1, AccountGender.FEMALE));
            assertEquals(
                303,
                repository.load().stream().filter(
                    value -> value.itemId() == 1039
                ).findFirst().orElseThrow().defaultPalette1Id()
            );
        } finally {
            database.execute("UPDATE item_definitions SET gender = 1, default_palette_1_id = 405 WHERE item_id = 1039");
        }

        for (String change : List.of(
            "gender = 3",
            "equipment_slot = 16",
            "default_palette_1_id = 65536",
            "name = ''"
        )) {
            assertThrows(
                SQLException.class,
                () -> database.execute(
                    "UPDATE item_definitions SET " + change + " WHERE item_id = 1039"
                )
            );
        }
    }

    @Test
    void atomicallySwapsOwnedInstancesAndRollsBackPartialOrForeignPlacements() throws Exception {
        var owner = database.account("owner@example.test", AccountGender.MALE);
        long character = database.create(owner.id(), "MaleHero", AccountGender.MALE).catalog().getFirst().id().value();
        long bag = container(character, "BAG");
        long equipment = container(character, "EQUIPMENT");
        database.execute("SELECT setval(pg_get_serial_sequence('inventory_items', 'id'), 4294967296)");
        long uid = database.scalar(
            "INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, "
                + "palette_2_id, palette_3_id) VALUES (" + bag + ", 79, 1039, 303, 261, 537) RETURNING id"
        );
        var actor = new InventoryActor(UUID.randomUUID(), owner.id(), character, AccountGender.MALE);
        var repository = new JdbcInventoryRepository(database.source(), 5);
        var action = new MoveInventoryItemAction(
            id -> Optional.of(actor),
            repository,
            InventoryFixtures.catalog(),
            new TestInventoryMoveContextAccess()
        );
        var command = new MoveInventoryItemCommand(
            owner.id().value(),
            new InventoryMove(
                bag,
                uid,
                79,
                1039,
                equipment,
                1,
                729
            )
        );
        var result = action.execute(actor.sessionId(), command);

        assertEquals(InventoryMoveOutcome.MOVED, result.outcome());
        assertTrue(uid > 0xffff_ffffL);
        assertEquals(equipment, database.scalar("SELECT inventory_id FROM inventory_items WHERE id = " + uid));
        assertEquals(303, database.scalar("SELECT palette_1_id FROM inventory_items WHERE id = " + uid));
        assertEquals(261, database.scalar("SELECT palette_2_id FROM inventory_items WHERE id = " + uid));
        assertEquals(537, database.scalar("SELECT palette_3_id FROM inventory_items WHERE id = " + uid));
        assertEquals(
            729,
            database.scalar(
                "SELECT item_id FROM inventory_items WHERE inventory_id = " + bag + " AND slot = 79"
            )
        );
        assertEquals(
            1039,
            database.characters().findByAccount(
                owner.id()
            ).getFirst().appearance().parts().get(
                1
            ).itemId()
        );

        try (var transaction = repository.beginTransaction(owner.id(), character)) {
            transaction.apply(List.of(new ItemPlacement(new InventoryItemId(uid), new InventoryId(bag), 0)));
        }

        assertEquals(equipment, database.scalar("SELECT inventory_id FROM inventory_items WHERE id = " + uid));
        var other = database.account("other@example.test", AccountGender.MALE);
        long otherCharacter = database.create(
            other.id(),
            "OtherHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();
        long foreignBag = container(otherCharacter, "BAG");

        assertThrows(InventoryPersistenceException.class, () -> repository.beginTransaction(other.id(), character));

        try (var transaction = repository.beginTransaction(owner.id(), character)) {
            assertThrows(
                InventoryPersistenceException.class,
                () -> transaction.apply(
                    List.of(
                        new ItemPlacement(new InventoryItemId(uid), new InventoryId(foreignBag), 0)
                    )
                )
            );
        }

        assertEquals(equipment, database.scalar("SELECT inventory_id FROM inventory_items WHERE id = " + uid));
        assertThrows(
            SQLException.class,
            () -> database.execute(
                "INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, "
                    + "palette_2_id, palette_3_id) VALUES (" + bag + ", 79, 1039, 0, 0, 0)"
            )
        );
        assertThrows(
            SQLException.class,
            () -> database.execute(
                "UPDATE inventory_items SET item_id = 4294967295 WHERE id = " + uid
            )
        );
    }

    @Test
    void serializesConcurrentMovesAndRejectsTheStaleSourceInsteadOfDuplicatingAnInstance() throws Exception {
        var owner = database.account("race@example.test", AccountGender.MALE);
        long character = database.create(owner.id(), "RaceHero", AccountGender.MALE).catalog().getFirst().id().value();
        long bag = container(character, "BAG");
        long equipment = container(character, "EQUIPMENT");
        long uid = database.scalar(
            "SELECT id FROM inventory_items WHERE inventory_id = " + equipment + " AND slot = 1"
        );
        var actor = new InventoryActor(UUID.randomUUID(), owner.id(), character, AccountGender.MALE);
        var action = new MoveInventoryItemAction(
            id -> Optional.of(
                actor
            ),
            new JdbcInventoryRepository(
                database.source(),
                5
            ),
            InventoryFixtures.catalog(),
            new TestInventoryMoveContextAccess()
        );
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return action.execute(
                        actor.sessionId(),
                        new MoveInventoryItemCommand(
                            owner.id().value(),
                            new InventoryMove(
                                equipment,
                                uid,
                                1,
                                729,
                                bag,
                                0,
                                0
                            )
                        )
                    ).outcome();
                }
            );
            var second = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return action.execute(
                        actor.sessionId(),
                        new MoveInventoryItemCommand(
                            owner.id().value(),
                            new InventoryMove(
                                equipment,
                                uid,
                                1,
                                729,
                                bag,
                                79,
                                0
                            )
                        )
                    ).outcome();
                }
            );
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertTrue(outcomes.contains(InventoryMoveOutcome.MOVED));
            assertTrue(outcomes.contains(InventoryMoveOutcome.STALE_SOURCE));
        }

        assertEquals(1, database.scalar("SELECT count(*) FROM inventory_items WHERE id = " + uid));
        assertEquals(4, database.scalar("SELECT count(*) FROM inventory_items"));
    }

    @Test
    void executesTheAdministrativeGrantScriptAndRollsBackInvalidOrFullBagGrants() throws Exception {
        var owner = database.account("grant@example.test", AccountGender.MALE);
        long character = database.create(owner.id(), "GrantHero", AccountGender.MALE).catalog().getFirst().id().value();
        grant(character, 1039, 303);
        long bag = container(character, "BAG");

        assertEquals(303, database.scalar("SELECT palette_1_id FROM inventory_items WHERE inventory_id = " + bag));
        assertThrows(SQLException.class, () -> grant(character, 636, 0));
        assertThrows(SQLException.class, () -> grant(character, 1039, 65536));
        assertThrows(SQLException.class, () -> grant(4294967295L, 1039, 0));
        database.execute(
            "INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, "
                + "palette_2_id, palette_3_id) SELECT " + bag
                + ", slot, 1039, 405, 0, 0 FROM generate_series(1, 79) slot"
        );

        assertThrows(SQLException.class, () -> grant(character, 1039, 0));
        assertEquals(80, database.scalar("SELECT count(*) FROM inventory_items WHERE inventory_id = " + bag));
    }

    @Test
    void rejectsCorruptDefinitionRowsAndSafelyPropagatesConnectionTransactionAndCleanupFailures() throws Exception {
        for (var failures : List.of(Set.of("source.getConnection"), Set.of("statement.executeQuery"))) {
            var source = InventoryJdbcFaults.source(database.source(), failures, null);
            assertThrows(InventoryPersistenceException.class, () -> new JdbcItemDefinitionRepository(source, 5).load());
        }

        var invalid = InventoryJdbcFaults.source(database.source(), Set.of(), 3);
        assertThrows(InventoryPersistenceException.class, () -> new JdbcItemDefinitionRepository(invalid, 5).load());
        var owner = database.account("failures@example.test", AccountGender.MALE);
        long character = database.create(
            owner.id(),
            "FailureHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();

        for (var failures : List.of(
            Set.of(
                "source.getConnection"
            ),
            Set.of(
                "connection.setAutoCommit",
                "connection.close"
            )
        )) {
            var repository = new JdbcInventoryRepository(
                InventoryJdbcFaults.source(
                    database.source(),
                    failures,
                    null
                ),
                5
            );
            assertThrows(InventoryPersistenceException.class, () -> repository.beginTransaction(owner.id(), character));
        }

        var reads = new JdbcInventoryRepository(
            InventoryJdbcFaults.source(
                database.source(),
                Set.of(
                    "rows.getLong"
                ),
                null
            ),
            5
        );

        try (var transaction = reads.beginTransaction(owner.id(), character)) {
            assertThrows(InventoryPersistenceException.class, transaction::state);
        }

        var commits = new JdbcInventoryRepository(
            InventoryJdbcFaults.source(
                database.source(),
                Set.of(
                    "connection.commit"
                ),
                null
            ),
            5
        );

        try (var transaction = commits.beginTransaction(owner.id(), character)) {
            assertThrows(InventoryPersistenceException.class, transaction::commit);
        }

        for (var failures : List.of(Set.of("connection.rollback"), Set.of("connection.close"))) {
            var repository = new JdbcInventoryRepository(
                InventoryJdbcFaults.source(
                    database.source(),
                    failures,
                    null
                ),
                5
            );
            var transaction = repository.beginTransaction(owner.id(), character);

            assertThrows(InventoryPersistenceException.class, transaction::close);
        }
    }

    private void grant(long character, long item, int palette) throws Exception {
        String sql = Files.readString(Path.of("scripts/grant-inventory-item.sql"))
            .replace("\\set ON_ERROR_STOP on\n", "")
            .replace(":'character_id'", "'" + character + "'").replace(":'item_id'", "'" + item + "'")
            .replace(
                ":'palette_1_id'",
                "'" + palette + "'"
            ).replace(
                ":'palette_2_id'",
                "'0'"
            ).replace(
                ":'palette_3_id'",
                "'0'"
            );
        database.execute(sql);
    }

    private long container(long character, String kind) throws SQLException {
        return database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = '" + kind + "'"
        );
    }
}
