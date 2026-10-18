package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;
import static taan.online.RoomScenario.map;
import static taan.online.RoomScenario.ready;
import static taan.online.RoomScenario.settings;
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
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionChangeGameOption;

class RoomSettingsIntegrationTest {
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
    void synchronizesSettingsListingsAndNewEntrantsWhilePreservingReadyEquipmentAndIsolation() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("observer");
        var d = player("foreign");
        var character = database.characters().findByAccount(b.id()).getFirst();
        var inventory = inventory(b);
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var listings = new LobbyRoomListCodec();

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
                guest.send(weapon(1002, 0));
                wire.changeWeapon(owner.read());
                wire.changeWeapon(guest.read());
                guest.send(ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                owner.sendFragmented(root("04-ChangeGameOption.request"));
                var expected = new PcGameSessionChangeGameOption(2, 1, 0, 255);

                assertEquals(expected, wire.settings(owner.read()));
                assertEquals(expected, wire.settings(guest.read()));
                var listing = listings.update(observer.read()).rooms().getFirst();
                assertEquals(2, listing.mapId());
                assertEquals(1, listing.gameType());
                assertEquals(0, listing.rule());
                owner.sendTogether(settings(2, 1, 0, 0), settings(2, 1, 0, 255));

                for (int repeat = 0; repeat < 2; repeat++) {
                    assertEquals(expected, wire.settings(owner.read()));
                    assertEquals(expected, wire.settings(guest.read()));
                }

                cash(observer);
                cash(foreign);
                owner.send(settings(200, 1, 0, 255));
                assertEquals(expected, wire.settings(owner.read()));
                rejectionNotice(owner);
                guest.send(settings(1, 0, 2, 255));
                assertEquals(expected, wire.settings(guest.read()));
                rejectionNotice(guest);
                cash(owner);
                cash(observer);
                cash(foreign);
                owner.send(map(4));

                assertEquals(new PcGameSessionChangeGameOption(4, 1, 0, 255), wire.settings(owner.read()));
                assertEquals(4, wire.settings(guest.read()).mapId());
                assertEquals(4, listings.update(observer.read()).rooms().getFirst().mapId());
                observer.send(RoomEntryFixtures.enter(room));
                var init = wire.initialize(observer.read());
                wire.slot(owner.read());
                wire.slot(guest.read());

                assertEquals(4, init.info().details().mapId());
                assertEquals(1, init.info().details().gameType());
                assertEquals(0, init.info().details().rule());
                assertEquals(3, init.slots().get(2).state());
                assertEquals(0, init.slots().get(2).occupant().weaponId());
                owner.disconnect();
                wire.slot(observer.read());
                wire.option(observer.read());
                wire.slot(guest.read());
                wire.option(guest.read());
                observer.send(settings(17, 0, 2, 255));

                assertEquals(new PcGameSessionChangeGameOption(17, 0, 2, 255), wire.settings(observer.read()));
                assertEquals(17, wire.settings(guest.read()).mapId());
                guest.send(weapon(1002, 0));
                cash(guest);
                cash(observer);
                guest.send(Arrays.copyOf(map(1), 7));

                assertEquals(2, wire.slot(observer.read()).slot().slotIndex());
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
            try (var owner = new CharacterTestClient(config.port())) {
                lobby(owner, a, 1);
                owner.send(root("create-unprotected"));
                var init = wire.initialize(owner.read());

                assertEquals(1, init.info().details().mapId());
                assertEquals(0, init.info().details().gameType());
                assertEquals(2, init.info().details().rule());
                assertEquals(1, init.info().details().playerCount());
                assertEquals(character, database.characters().findByAccount(b.id()).getFirst());
            }
        }
    }

    @Test
    void resyncsAllUnsupportedRequestsThenAcceptsTheNextValidChange() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var initial = new PcGameSessionChangeGameOption(1, 0, 2, 255);

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
                guest.send(settings(1, 1, 0, 255));

                assertArrayEquals(integer(root("PcChangeGameOption-rejected-current"), 9, 255, 1), guest.read());
                rejectionNotice(guest);

                for (byte[] request : new byte[][]{
                    map(0),
                    map(3),
                    map(200),
                    map(202),
                    map(300),
                    map(500),
                    settings(1, 2, 0, 255),
                    settings(1, 3, 2, 255),
                    settings(1, 0, 0, 255),
                    settings(1, 1, 2, 255)
                }) {
                    owner.send(request);
                    assertEquals(initial, wire.settings(owner.read()));
                    rejectionNotice(owner);
                    cash(guest);
                }

                owner.sendTogether(map(2), settings(4, 1, 0, 255));

                assertEquals(new PcGameSessionChangeGameOption(2, 0, 2, 255), wire.settings(owner.read()));
                assertEquals(2, wire.settings(guest.read()).mapId());
                assertEquals(new PcGameSessionChangeGameOption(4, 1, 0, 255), wire.settings(owner.read()));
                assertEquals(4, wire.settings(guest.read()).mapId());
                owner.send(Arrays.copyOf(settings(1, 0, 2, 255), 9));

                assertEquals(0, wire.slot(guest.read()).slot().state());
                wire.option(guest.read());
                cash(guest);
            }
        }
    }

    private void rejectionNotice(CharacterTestClient client) throws Exception {
        assertEquals(
            "Requested room settings are not allowed.",
            new NoticeMessageCodec().decode(client.read()).message()
        );
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
    }
}
