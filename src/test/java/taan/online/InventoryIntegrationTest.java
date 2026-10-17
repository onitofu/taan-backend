package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.inventory.protocol.packet.Inventory;
import taan.online.inventory.protocol.packet.InventoryItemData;

class InventoryIntegrationTest {
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
    void preservesGrantedInstancesAndAppearanceAcrossMoveReloginAndServerRestart() throws Exception {
        var account = database.account("inventory@example.test", AccountGender.MALE);
        long character = database.create(
            account.id(),
            "MaleHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();
        long bag = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'BAG'"
        );
        long equipment = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'EQUIPMENT'"
        );
        database.execute("SELECT setval(pg_get_serial_sequence('inventory_items', 'id'), 4294967296)");
        long normal = grant(bag, 0, 405);
        long red = grant(bag, 1, 303);
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var original = select(client, account.login().value(), character, 729, 295);

                assertEquals(80, original.getFirst().capacity());
                assertEquals(
                    List.of(
                        normal,
                        red
                    ),
                    original.getFirst().items().stream().map(
                        InventoryItemData::instanceId
                    ).toList()
                );
                client.sendTogether(ChannelFixtures.move(1), ChannelFixtures.enter(1));

                assertEquals(0x33cf0634, ChannelFixtures.type(client.read()));
                assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
                assertEquals(0x500c07d7, ChannelFixtures.type(client.read()));
                assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));

                client.sendFragmented(
                    InventoryFixtures.move(
                        account.id().value(),
                        bag,
                        normal,
                        0,
                        1039,
                        equipment,
                        1,
                        729
                    )
                );
                var changedBag = InventoryFixtures.update(client.read());
                var changedEquipment = InventoryFixtures.update(client.read());
                moved(client.read(), bag, equipment);

                assertEquals(729, changedBag.items().getFirst().itemId());
                assertEquals(normal, at(changedEquipment, 1).instanceId());
                assertEquals(405, at(changedEquipment, 1).palette1Id());

                client.send(InventoryFixtures.move(account.id().value(), bag, red, 1, 1039, bag, 79, 0));
                var reordered = InventoryFixtures.update(client.read());
                moved(client.read(), bag, bag);

                assertEquals(red, at(reordered, 79).instanceId());
                client.send(InventoryFixtures.move(account.id().value(), bag, red, 79, 1039, equipment, 1, 1039));
                changedBag = InventoryFixtures.update(client.read());
                changedEquipment = InventoryFixtures.update(client.read());
                moved(client.read(), bag, equipment);

                assertEquals(normal, at(changedBag, 79).instanceId());
                assertEquals(red, at(changedEquipment, 1).instanceId());
                assertEquals(303, at(changedEquipment, 1).palette1Id());

                client.sendTogether(
                    InventoryFixtures.move(
                        account.id().value(),
                        bag,
                        normal,
                        79,
                        1039,
                        equipment,
                        1,
                        729
                    ),
                    SelectionFixtures.cash(
                        0,
                        0
                    )
                );

                assertEquals(changedBag, InventoryFixtures.update(client.read()));
                assertEquals(changedEquipment, InventoryFixtures.update(client.read()));
                assertEquals(0x1d380495, ChannelFixtures.type(client.read()));
                client.disconnect();
            }

            try (var client = new CharacterTestClient(config.port())) {
                var restored = select(client, account.login().value(), character, 1039, 303);

                assertEquals(bag, restored.getFirst().id());
                assertEquals(equipment, restored.get(1).id());
                assertEquals(red, at(restored.get(1), 1).instanceId());
                assertEquals(normal, at(restored.getFirst(), 79).instanceId());
                client.disconnect();
            }
        }

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var restored = select(client, account.login().value(), character, 1039, 303);

                assertEquals(red, at(restored.get(1), 1).instanceId());
                assertEquals(303, at(restored.get(1), 1).palette1Id());
                assertEquals(normal, at(restored.getFirst(), 79).instanceId());
                assertEquals(1, at(restored.get(1), 1).quantity());
                client.disconnect();
            }
        }
    }

    @Test
    void rejectsForeignAndOutOfBoundsRequestsAndKeepsTheClientAbleToContinue() throws Exception {
        var account = database.account("owner@example.test", AccountGender.MALE);
        var other = database.account("other@example.test", AccountGender.MALE);
        long character = database.create(
            account.id(),
            "MaleHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();
        long foreign = database.create(other.id(), "OtherHero", AccountGender.MALE).catalog().getFirst().id().value();
        long bag = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'BAG'"
        );
        long equipment = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'EQUIPMENT'"
        );
        long foreignBag = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + foreign + " AND kind = 'BAG'"
        );
        long uid = database.scalar(
            "SELECT id FROM inventory_items WHERE inventory_id = " + equipment + " AND slot = 1"
        );
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var client = new CharacterTestClient(config.port())) {
                var initial = select(client, account.login().value(), character, 729, 295);

                for (byte[] request : List.of(
                    InventoryFixtures.move(other.id().value(), equipment, uid, 1, 729, bag, 0, 0),
                    InventoryFixtures.move(account.id().value(), equipment, uid, 1, 729, foreignBag, 0, 0),
                    InventoryFixtures.move(account.id().value(), equipment, uid, 1, 729, bag, 80, 0)
                )) {
                    client.sendTogether(request, SelectionFixtures.cash(0, 0));
                    var first = InventoryFixtures.update(client.read());
                    var second = InventoryFixtures.update(client.read());

                    assertTrue(List.of(bag, equipment).contains(first.id()));
                    assertTrue(List.of(bag, equipment).contains(second.id()));
                    assertEquals(initial.get(1), first.id() == equipment ? first : second);
                    assertEquals(0x1d380495, ChannelFixtures.type(client.read()));
                }

                client.disconnect();
            }
        }
    }

    @Test
    void reconnectsDuringCommitWithoutLosingTheDurableMoveOrApplyingAnOldReplyToTheNewSession() throws Exception {
        var account = database.account("commit@example.test", AccountGender.MALE);
        long character = database.create(
            account.id(),
            "CommitHero",
            AccountGender.MALE
        ).catalog().getFirst().id().value();
        long bag = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'BAG'"
        );
        long equipment = database.scalar(
            "SELECT id FROM inventories WHERE character_id = " + character + " AND kind = 'EQUIPMENT'"
        );
        long uid = grant(bag, 0, 303);
        database.execute(
            "CREATE FUNCTION hold_inventory_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "PERFORM pg_advisory_xact_lock(78102211); RETURN NEW; END $$"
        );
        database.execute(
            "CREATE CONSTRAINT TRIGGER hold_inventory_commit AFTER UPDATE ON inventory_items "
                + "DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION hold_inventory_commit()"
        );
        var config = database.configuration();

        try (var application = new Application(config)) {
            application.start();

            try (var gate = database.source().getConnection()) {
                gate.setAutoCommit(false);

                try (var statement = gate.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(78102211)");
                }

                try (var client = new CharacterTestClient(config.port())) {
                    select(client, account.login().value(), character, 729, 295);
                    client.send(InventoryFixtures.move(account.id().value(), bag, uid, 0, 1039, equipment, 1, 729));
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

                    while (database.scalar(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted"
                    ) == 0
                        && System.nanoTime() < deadline) {
                        Thread.sleep(10);
                    }

                    assertTrue(
                        database.scalar(
                            "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted"
                        ) > 0
                    );
                    client.disconnect();

                    try (var worker = Executors.newSingleThreadExecutor()) {
                        var reconnecting = worker.submit(
                            () -> {
                                try (var next = new CharacterTestClient(config.port())) {
                                    var result = select(next, account.login().value(), character, 1039, 303);
                                    next.disconnect();
                                    return result;
                                }
                            }
                        );
                        gate.commit();
                        var restored = reconnecting.get(10, TimeUnit.SECONDS);

                        assertEquals(uid, at(restored.get(1), 1).instanceId());
                        assertEquals(303, at(restored.get(1), 1).palette1Id());
                    }
                } finally {
                    gate.rollback();
                }
            }
        } finally {
            database.execute("DROP TRIGGER hold_inventory_commit ON inventory_items");
            database.execute("DROP FUNCTION hold_inventory_commit()");
        }
    }

    private List<Inventory> select(
        CharacterTestClient client,
        String login,
        long character,
        long hair,
        int palette
    ) throws Exception {
        var catalog = CharacterTestClient.catalog(client.login(login), 37).getFirst();
        var appearance = ByteBuffer.wrap(catalog.parts()).order(ByteOrder.LITTLE_ENDIAN);
        appearance.position(10);

        assertEquals(hair, Integer.toUnsignedLong(appearance.getInt()));
        assertEquals(palette, Short.toUnsignedInt(appearance.getShort()));
        client.send(CharacterFixtures.select(character));
        var result = InventoryFixtures.selected(client.read());
        SelectionFixtures.assertPlayableTime(client.read());
        return result;
    }

    private InventoryItemData at(Inventory inventory, int slot) {
        return inventory.items().stream().filter(item -> item.slot() == slot).findFirst().orElseThrow();
    }

    private void moved(byte[] root, long source, long destination) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x4b9907b6, input.getInt());
        assertEquals(source, Integer.toUnsignedLong(input.getInt()));
        assertEquals(destination, Integer.toUnsignedLong(input.getInt()));
        assertEquals(0, input.remaining());
    }

    private long grant(long bag, int slot, int palette) throws Exception {
        return database.scalar(
            "INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, "
                + "palette_2_id, palette_3_id) VALUES (" + bag + ", " + slot + ", 1039, "
                + palette + ", 0, 0) RETURNING id"
        );
    }
}
