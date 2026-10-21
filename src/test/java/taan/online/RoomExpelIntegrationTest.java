package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

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
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomExpelIntegrationTest {
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
    void freesTheSlotAndReturnsTheTargetToLobbyWithoutTouchingPersistentDataOrOtherChannels() throws Exception {
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
                RoomIntegrationSupport.enterLobby(database, owner, a, 1);
                RoomIntegrationSupport.enterLobby(database, guest, b, 1);
                owner.read();
                RoomIntegrationSupport.enterLobby(database, observer, c, 1);
                owner.read();
                guest.read();
                RoomIntegrationSupport.enterLobby(database, foreign, d, 2);
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
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                guest.send(RoomScenario.expel(1001));
                owner.send(RoomScenario.expel(2));
                RoomIntegrationSupport.assertBalances(guest, 0, 0);
                RoomIntegrationSupport.assertBalances(owner, 0, 0);
                owner.sendFragmented(root("PqGameSession_UserExpel-native"));

                assertArrayEquals(root("PcGameSession_UserExpel-guest"), guest.read());
                var removed = wire.slot(owner.read());
                assertEquals(2, removed.slot().slotIndex());
                assertEquals(0, removed.slot().state());
                assertEquals(1, removed.resetWidget());
                assertEquals(1, listings.update(observer.read()).rooms().getFirst().playerCount());
                owner.send(RoomScenario.expel(1002));
                RoomIntegrationSupport.assertBalances(owner, 0, 0);
                RoomIntegrationSupport.assertBalances(guest, 0, 0);
                RoomIntegrationSupport.assertBalances(observer, 0, 0);
                RoomIntegrationSupport.assertBalances(foreign, 0, 0);
                guest.sendTogether(RoomScenario.leave(room, 0), ChannelFixtures.enter(0));

                assertEquals(1, wire.leave(guest.read()).success());
                guest.read();
                var lobbyRooms = listings.initialize(guest.read()).rooms();
                guest.read();
                observer.read();
                assertEquals(1, lobbyRooms.size());
                assertEquals(room, lobbyRooms.getFirst().roomId());
                assertEquals(1, lobbyRooms.getFirst().playerCount());
                guest.send(RoomScenario.leave(room, 0));
                assertEquals(1, wire.leave(guest.read()).success());
                guest.send(RoomEntryFixtures.enter(room));
                var rejoined = wire.initialize(guest.read());
                wire.slot(owner.read());
                observer.read();
                observer.read();

                assertEquals(2, rejoined.slots().get(2).state());
                assertEquals(1002, rejoined.slots().get(2).occupant().characterId());
                owner.send(Arrays.copyOf(RoomScenario.expel(1002), 7));
                owner.awaitClosure();
                wire.slot(guest.read());
                assertEquals(b.id().value(), wire.option(guest.read()).option().ownerAccountId());
                assertEquals(1, listings.update(observer.read()).rooms().getFirst().playerCount());
                RoomIntegrationSupport.assertBalances(guest, 0, 0);
                RoomIntegrationSupport.assertBalances(foreign, 0, 0);
                assertEquals(character, database.characters().findByAccount(b.id()).getFirst());
                assertEquals(inventory, inventory(b));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("bravo@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }

        try (var app = new Application(config)) {
            app.start();
            try (var guest = new CharacterTestClient(config.port())) {
                RoomIntegrationSupport.enterLobby(database, guest, b, 1);
                guest.send(root("create-unprotected"));

                assertEquals(1, wire.initialize(guest.read()).info().details().playerCount());
                assertEquals(character, database.characters().findByAccount(b.id()).getFirst());
                assertEquals(inventory, inventory(b));
            }
        }
    }

    @Test
    void handlesCoalescedRepeatedKicksAndTargetDisconnectWithoutWaitingForLeave() throws Exception {
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
                RoomIntegrationSupport.enterLobby(database, owner, a, 1);
                RoomIntegrationSupport.enterLobby(database, guest, b, 1);
                owner.read();
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                owner.sendTogether(RoomScenario.expel(1002), RoomScenario.expel(1002));

                assertEquals(2, wire.expel(guest.read()).targetAccountId());
                assertEquals(0, wire.slot(owner.read()).slot().state());
                RoomIntegrationSupport.assertBalances(owner, 0, 0);
                RoomIntegrationSupport.assertBalances(guest, 0, 0);
                guest.disconnect();
                RoomIntegrationSupport.assertBalances(owner, 0, 0);

                try (var reconnected = new CharacterTestClient(config.port())) {
                    RoomIntegrationSupport.enterLobby(database, reconnected, b, 1);
                    reconnected.send(RoomEntryFixtures.enter(room));
                    var init = wire.initialize(reconnected.read());

                    assertEquals(2, init.info().details().playerCount());
                    assertEquals(1002, init.slots().get(2).occupant().characterId());
                    assertEquals(2, wire.slot(owner.read()).slot().slotIndex());
                    RoomIntegrationSupport.assertBalances(owner, 0, 0);
                }
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
}
