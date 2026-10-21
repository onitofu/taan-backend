package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.character.protocol.packet.CharacterPart;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.protocol.codec.InventoryUpdateCodec;
import taan.online.inventory.protocol.packet.PrMoveInventoryItem;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomInventoryIntegrationTest {
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
        database.execute("ALTER SEQUENCE characters_id_seq RESTART WITH 1001");
    }

    @Test
    void synchronizesCommittedAppearanceAndPreservesInventoryRoomStateAndChannelIsolation() throws Exception {
        var a = player("alpha", AccountGender.MALE);
        var b = player("bravo", AccountGender.FEMALE);
        var c = player("observer", AccountGender.FEMALE);
        var d = player("foreign", AccountGender.FEMALE);
        long bag = container(1001, "BAG");
        long equipment = container(1001, "EQUIPMENT");
        long uid = grant(bag, 0);
        long guestBag = container(1002, "BAG");
        long guestEquipment = container(1002, "EQUIPMENT");
        long spare = grant(guestBag, 0);
        long guestHair = database.scalar("SELECT id FROM inventory_items WHERE inventory_id = " + guestEquipment
            + " AND slot = 1");
        long guestHairType = database.scalar("SELECT item_id FROM inventory_items WHERE id = " + guestHair);
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var expected = new CharacterPart(1039, 303, 261, 197);
        List<CharacterInventory> saved;

        try (var app = new Application(config); var logs = new LogCapture()) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port());
                var observer = new CharacterTestClient(config.port());
                var foreign = new CharacterTestClient(config.port())
            ) {
                lobby(owner, a, 1);
                lobby(guest, b, 1);
                owner.read();
                lobby(observer, c, 1);
                owner.read();
                guest.read();
                lobby(foreign, d, 2);
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                foreign.send(root("create-unprotected"));
                wire.initialize(foreign.read());
                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                observer.read();
                observer.read();
                owner.send(RoomScenario.weapon(1001, 0));
                wire.changeWeapon(owner.read());
                wire.changeWeapon(guest.read());
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                owner.sendFragmented(InventoryFixtures.move(a.id().value(), bag, uid, 0, 1039, equipment, 1, 729));
                var changed = wire.slot(owner.read());

                assertEquals(expected, changed.slot().occupant().appearance().parts().get(1));
                assertEquals(changed, wire.slot(guest.read()));
                assertEquals(0, changed.slot().occupant().weaponId());
                assertEquals(2, changed.slot().state());
                assertEquals(1, changed.resetWidget());
                assertEquals(bag, InventoryFixtures.update(owner.read()).id());
                assertEquals(equipment, InventoryFixtures.update(owner.read()).id());
                assertArrayEquals(moveSound(bag, equipment), owner.read());
                cash(observer);
                cash(foreign);
                guest.send(InventoryFixtures.move(
                    b.id().value(),
                    guestEquipment,
                    guestHair,
                    1,
                    guestHairType,
                    guestBag,
                    20,
                    0
                ));

                assertEquals(guestEquipment, InventoryFixtures.update(guest.read()).id());
                assertEquals(guestBag, InventoryFixtures.update(guest.read()).id());
                assertEquals(
                    guestEquipment,
                    database.scalar("SELECT inventory_id FROM inventory_items WHERE id = "
                    + guestHair)
                );
                cash(owner);
                cash(guest);
                guest.send(InventoryFixtures.move(b.id().value(), guestBag, spare, 0, 1039, guestBag, 79, 0));
                InventoryFixtures.update(guest.read());
                assertArrayEquals(moveSound(guestBag, guestBag), guest.read());
                cash(owner);
                guest.send(RoomScenario.ready(0));
                wire.ready(owner.read());
                wire.ready(guest.read());
                guest.send(InventoryFixtures.move(
                    b.id().value(),
                    guestEquipment,
                    guestHair,
                    1,
                    guestHairType,
                    guestBag,
                    20,
                    0
                ));

                assertEquals(0, wire.slot(guest.read()).slot().occupant().appearance().parts().get(1).itemId());
                wire.slot(owner.read());
                InventoryFixtures.update(guest.read());
                InventoryFixtures.update(guest.read());
                guest.read();
                owner.send(InventoryFixtures.move(a.id().value(), equipment, uid, 1, 1039, bag, 2, 0));

                assertEquals(0, wire.slot(owner.read()).slot().occupant().appearance().parts().get(1).itemId());
                wire.slot(guest.read());
                InventoryFixtures.update(owner.read());
                InventoryFixtures.update(owner.read());
                owner.read();
                owner.send(InventoryFixtures.move(a.id().value(), bag, uid, 2, 1039, equipment, 1, 0));

                assertEquals(expected, wire.slot(owner.read()).slot().occupant().appearance().parts().get(1));
                wire.slot(guest.read());
                InventoryFixtures.update(owner.read());
                InventoryFixtures.update(owner.read());
                owner.read();
                observer.send(RoomEntryFixtures.enter(room));
                var init = wire.initialize(observer.read());
                wire.slot(owner.read());
                wire.slot(guest.read());

                assertEquals(expected, init.slots().getFirst().occupant().appearance().parts().get(1));
                assertEquals(0, init.slots().getFirst().occupant().weaponId());
                assertEquals(3, init.info().details().playerCount());
                saved = inventories(a);
                owner.sendTogether(RoomScenario.leave(room, 0), RoomScenario.leave(room, 0));

                assertEquals(1, wire.leave(owner.read()).success());
                assertEquals(1, wire.leave(owner.read()).success());
                wire.slot(guest.read());
                wire.option(guest.read());
                wire.slot(observer.read());
                wire.option(observer.read());
                owner.send(ChannelFixtures.enter(0));
                owner.read();
                owner.read();
                owner.read();
                cash(owner);
                cash(foreign);
                assertEquals(saved, inventories(a));
                assertTrue(logs.text().contains("room.appearance.updated"));
                assertTrue(logs.text().contains("STATE_RESTRICTED"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("alpha@example.test"));
            }
        }

        try (var app = new Application(config)) {
            app.start();
            try (var owner = new CharacterTestClient(config.port())) {
                lobby(owner, a, 1);
                owner.send(root("create-unprotected"));
                var init = wire.initialize(owner.read());

                assertEquals(expected, init.slots().getFirst().occupant().appearance().parts().get(1));
                assertEquals(saved, inventories(a));
                assertEquals(
                    uid,
                    database.scalar("SELECT id FROM inventory_items WHERE inventory_id = " + equipment
                    + " AND slot = 1")
                );
            }
        }
    }

    @Test
    void rollsBackACommitFailureWithoutPublishingUncommittedAppearance() throws Exception {
        var a = player("alpha", AccountGender.MALE);
        var b = player("bravo", AccountGender.FEMALE);
        long bag = container(1001, "BAG");
        long equipment = container(1001, "EQUIPMENT");
        long uid = grant(bag, 0);
        var original = database.characters().findByAccount(a.id()).getFirst();
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        database.execute("CREATE FUNCTION reject_equipment_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
            + "RAISE EXCEPTION 'private@example.test'; END $$");
        database.execute("CREATE CONSTRAINT TRIGGER reject_equipment_commit AFTER UPDATE ON inventory_items "
            + "DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_equipment_commit()");

        try (var app = new Application(config); var logs = new LogCapture()) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                lobby(owner, a, 1);
                lobby(guest, b, 1);
                owner.read();
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                owner.send(InventoryFixtures.move(a.id().value(), bag, uid, 0, 1039, equipment, 1, 729));
                owner.awaitClosure();
                var slot = wire.slot(guest.read());

                assertEquals(0, slot.slot().state());
                wire.option(guest.read());
                cash(guest);
                assertEquals(original, database.characters().findByAccount(a.id()).getFirst());
                assertEquals(bag, database.scalar("SELECT inventory_id FROM inventory_items WHERE id = " + uid));
                assertFalse(logs.text().contains("room.appearance.updated"));
                assertFalse(logs.text().contains("private@example.test"));
                assertTrue(logs.text().contains("inventory.move.failed"));
            }
        } finally {
            database.execute("DROP TRIGGER reject_equipment_commit ON inventory_items");
            database.execute("DROP FUNCTION reject_equipment_commit()");
        }
    }

    private long grant(long inventory, int slot) throws Exception {
        return database.scalar("INSERT INTO inventory_items (inventory_id, slot, item_id, palette_1_id, "
            + "palette_2_id, palette_3_id) VALUES (" + inventory + ", " + slot + ", 1039, 303, 261, 197) RETURNING id");
    }

    private long container(long character, String kind) throws Exception {
        return database.scalar("SELECT id FROM inventories WHERE character_id = " + character + " AND kind = '" + kind
            + "'");
    }

    private List<CharacterInventory> inventories(Account account) {
        try (var transaction = database.characters().beginTransaction(account.id())) {
            var character = transaction.catalog().getFirst();
            var result = transaction.inventories(character.id());
            transaction.commit();
            return result;
        }
    }

    private Account player(String name, AccountGender gender) {
        var account = database.account(name + "@example.test", gender);
        assertFalse(database.create(account.id(), "Hero" + name, gender).catalog().isEmpty());
        return account;
    }

    private void lobby(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        var character = database.characters().findByAccount(account.id()).getFirst();
        client.send(CharacterFixtures.select(character.id().value()));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        client.read();
        client.read();
        client.read();
    }

    private byte[] moveSound(long source, long destination) {
        return new InventoryUpdateCodec().moved(new PrMoveInventoryItem(source, destination));
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
    }
}
