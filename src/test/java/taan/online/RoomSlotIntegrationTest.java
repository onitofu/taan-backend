package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;

import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomSlotIntegrationTest {
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
    void broadcastsChangesButNotRepeatsAndKeepsClosedPlacesAfterOwnerDisconnect() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("observer");
        var d = player("foreign");
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var lists = new LobbyRoomListCodec();

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
                owner.sendFragmented(root("PqGameSession_OpenCloseSlot-close"));

                assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), owner.read());
                cash(guest);
                cash(observer);
                cash(foreign);
                guest.send(RoomEntryFixtures.enter(room));
                var joined = wire.initialize(guest.read());

                assertEquals(1, joined.slots().get(1).state());
                assertEquals(2, wire.slot(owner.read()).slot().slotIndex());
                observer.read();
                assertEquals(2, lists.update(observer.read()).rooms().getFirst().playerCount());
                guest.sendTogether(root("PqGameSession_OpenCloseSlot-open"), SelectionFixtures.cash(0, 0));

                assertArrayEquals(SelectionFixtures.cash(0, 0), guest.read());
                assertArrayEquals(SelectionFixtures.money(0), guest.read());
                owner.sendTogether(
                    integer(root("PqGameSession_OpenCloseSlot-open"), 4, 255, 1),
                    integer(root("PqGameSession_OpenCloseSlot-open"), 5, 255, 1)
                );
                owner.sendTogether(
                    integer(root("PqGameSession_OpenCloseSlot-open"), 5, 2, 1),
                    root("PqGameSession_OpenCloseSlot-close")
                );

                assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), owner.read());
                cash(guest);
                cash(observer);
                owner.disconnect();

                assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), guest.read());
                assertArrayEquals(root("owner-transfer"), guest.read());
                assertEquals(1, lists.update(observer.read()).rooms().getFirst().playerCount());
                guest.send(root("PqGameSession_OpenCloseSlot-open"));

                assertArrayEquals(root("PcGameSession_OpenCloseSlot-open"), guest.read());
                cash(observer);
                cash(foreign);
                guest.disconnect();

                assertTrue(lists.initialize(observer.read()).rooms().isEmpty());
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("alpha@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }
    }

    @Test
    void blocksAdmissionToClosedPlacesAndInitializesTheReopenedLayoutBeforeMalformedDisconnect() throws Exception {
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
                var requests = new byte[7][];

                for (int index = 1; index < 8; index++) {
                    requests[index - 1] = integer(root("PqGameSession_OpenCloseSlot-close"), 5, index, 1);
                }

                for (byte[] request : requests) {
                    owner.send(request);
                }

                for (int index = 1; index < 8; index++) {
                    assertEquals(index, wire.slotAvailability(owner.read()).slotIndex());
                }

                guest.sendTogether(root("quickjoin-any"), RoomEntryFixtures.enter(room));

                assertArrayEquals(root("quickjoin-fail"), guest.read());
                assertArrayEquals(root("enter-fail-8"), guest.read());
                owner.send(integer(root("PqGameSession_OpenCloseSlot-open"), 5, 7, 1));

                assertEquals(7, wire.slotAvailability(owner.read()).slotIndex());
                guest.sendFragmented(root("quickjoin-any"));
                var joined = wire.initialize(guest.read());

                assertEquals(8, joined.info().details().capacity());
                assertEquals(2, joined.info().details().playerCount());
                assertTrue(joined.slots().subList(1, 7).stream().allMatch(slot -> slot.state() == 1));
                assertEquals(2, joined.slots().get(7).state());
                assertEquals(7, wire.slot(owner.read()).slot().slotIndex());
                byte[] valid = root("PqGameSession_OpenCloseSlot-open");
                guest.send(Arrays.copyOf(valid, valid.length + 1));

                assertEquals(7, wire.slot(owner.read()).slot().slotIndex());
                cash(owner);
            }
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
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        new LobbyRoomListCodec().initialize(client.read());
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
    }
}
