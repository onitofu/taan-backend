package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.ready;
import static taan.online.RoomScenario.team;
import static taan.online.RoomScenario.weapon;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.inventory.model.CharacterInventory;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomWeaponIntegrationTest {
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
    void replaysSelectionPreservesReadyAndRosterAndNeverChangesDurableEquipment() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("observer");
        var d = player("foreign");
        var character = database.characters().findByAccount(b.id()).getFirst();
        var inventory = inventory(b);
        var config = database.configuration();
        var wire = new RoomResponseCodec();

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
                foreign.send(root("create-unprotected"));
                wire.initialize(foreign.read());
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                observer.read();
                observer.read();
                guest.sendFragmented(root("03-ChangeWeapon.request"));

                assertArrayEquals(root("03-ChangeWeapon.reply"), owner.read());
                assertArrayEquals(root("03-ChangeWeapon.reply"), guest.read());
                guest.sendTogether(weapon(1002, 0), weapon(1002, 0));

                assertArrayEquals(root("03-ChangeWeapon.reply"), owner.read());
                assertArrayEquals(root("03-ChangeWeapon.reply"), guest.read());
                var selected = wire.changeWeapon(owner.read());
                assertEquals(1002, selected.characterId());
                assertEquals(0, selected.weaponId());
                assertEquals(selected, wire.changeWeapon(guest.read()));
                guest.sendTogether(weapon(2, 0), weapon(1001, 0));
                for (int unsupported : new int[]{1, 4, 11, 200, 100, 300}) {
                    guest.send(weapon(1002, unsupported));

                    assertEquals(0, wire.changeWeapon(guest.read()).weaponId());
                }

                cash(guest);
                cash(owner);
                cash(observer);
                cash(foreign);
                guest.send(ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                guest.send(weapon(1002, 0));
                cash(guest);
                cash(owner);
                owner.send(weapon(1001, 0));

                assertEquals(0, wire.changeWeapon(owner.read()).weaponId());
                assertEquals(0, wire.changeWeapon(guest.read()).weaponId());
                observer.send(RoomEntryFixtures.enter(room));
                var init = wire.initialize(observer.read());
                wire.slot(owner.read());
                wire.slot(guest.read());

                assertEquals(0, init.slots().get(0).occupant().weaponId());
                assertEquals(3, init.slots().get(2).state());
                assertEquals(0, init.slots().get(2).occupant().weaponId());
                assertEquals(1002, init.slots().get(2).occupant().characterId());
                guest.send(ready(0));
                wire.ready(owner.read());
                wire.ready(guest.read());
                wire.ready(observer.read());
                guest.sendTogether(team(1), weapon(1002, 0));

                assertEquals(4, wire.changeTeam(owner.read()).newSlot0());
                wire.changeTeam(guest.read());
                wire.changeTeam(observer.read());
                assertArrayEquals(root("03-ChangeWeapon.reply"), owner.read());
                assertArrayEquals(root("03-ChangeWeapon.reply"), guest.read());
                assertArrayEquals(root("03-ChangeWeapon.reply"), observer.read());
                owner.disconnect();
                wire.slot(observer.read());
                wire.option(observer.read());
                wire.slot(guest.read());
                wire.option(guest.read());
                guest.send(weapon(1002, 0));

                assertEquals(0, wire.changeWeapon(observer.read()).weaponId());
                assertEquals(0, wire.changeWeapon(guest.read()).weaponId());
                guest.send(Arrays.copyOf(weapon(1002, 0), 4));

                assertEquals(4, wire.slot(observer.read()).slot().slotIndex());
                cash(observer);
                cash(foreign);
                assertEquals(character, database.characters().findByAccount(b.id()).getFirst());
                assertEquals(inventory, inventory(b));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("bravo@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }

        try (var restarted = new Application(config)) {
            restarted.start();
            try (var guest = new CharacterTestClient(config.port())) {
                lobby(guest, b, 1);
                guest.send(root("create-unprotected"));
                var init = wire.initialize(guest.read());

                assertEquals(0, init.slots().get(0).occupant().weaponId());
                assertEquals(character, database.characters().findByAccount(b.id()).getFirst());
                assertEquals(inventory, inventory(b));
            }
        }
    }

    @Test
    void resetsSelectionAfterLeavingAndReenteringTheSameRoom() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var config = database.configuration();
        var wire = new RoomResponseCodec();

        try (var app = new Application(config)) {
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
                guest.send(weapon(1002, 0));
                wire.changeWeapon(owner.read());
                wire.changeWeapon(guest.read());
                guest.send(RoomScenario.leave(room, 0));
                wire.leave(guest.read());
                wire.slot(owner.read());
                guest.send(ChannelFixtures.enter(0));
                guest.read();
                guest.read();
                guest.read();
                guest.send(RoomEntryFixtures.enter(room));
                var init = wire.initialize(guest.read());
                wire.slot(owner.read());

                assertEquals(0, init.slots().get(2).occupant().weaponId());
                cash(guest);
            }
        }
    }

    private List<CharacterInventory> inventory(Account account) {
        try (var transaction = database.characters().beginTransaction(account.id())) {
            var character = transaction.catalog().getFirst();
            var result = transaction.inventories(character.id());
            transaction.commit();
            return result;
        }
    }

    private Account player(String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        assertFalse(database.create(account.id(), "Hero" + name, AccountGender.FEMALE).catalog().isEmpty());
        return account;
    }

    private void lobby(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        long character = database.characters().findByAccount(account.id()).getFirst().id().value();
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        client.read();
        client.read();
        client.read();
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
    }
}
