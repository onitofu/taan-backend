package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.lobby.protocol.packet.PcLobbyInitRoomList;
import taan.online.room.protocol.codec.RoomResponseCodec;

class QuickJoinIntegrationTest {
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
    void retriesWithoutBypassingPasswordsOrChannelsAndKeepsTheRoomAcrossLobbyLeave() throws Exception {
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
                guest.sendTogether(integer(root("quickjoin-any"), 4, 0, 2), root("quickjoin-any"));

                assertArrayEquals(root("quickjoin-fail"), guest.read());
                assertArrayEquals(root("quickjoin-fail"), guest.read());
                foreign.send(root("create-unprotected"));
                wire.initialize(foreign.read());
                owner.send(root("create-password"));
                int protectedRoom = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.send(root("quickjoin-any"));

                assertArrayEquals(root("quickjoin-fail"), guest.read());
                RoomIntegrationSupport.assertBalances(owner, 0, 0);
                RoomIntegrationSupport.assertBalances(observer, 0, 0);
                owner.send(RoomScenario.leave(protectedRoom, 0));
                wire.leave(owner.read());
                assertTrue(lists.initialize(guest.read()).rooms().isEmpty());
                assertTrue(lists.initialize(observer.read()).rooms().isEmpty());
                owner.send(ChannelFixtures.enter(0));
                bootstrap(owner);
                guest.read();
                observer.read();
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.sendFragmented(root("quickjoin-any"));
                var joined = wire.initialize(guest.read());
                var added = wire.slot(owner.read());
                observer.read();

                assertEquals(room, joined.info().roomId());
                assertEquals(joined.slots().get(2), added.slot());
                assertEquals(2, lists.update(observer.read()).rooms().getFirst().playerCount());
                guest.sendTogether(root("quickjoin-any"), ChannelFixtures.leave());

                assertArrayEquals(root("quickjoin-fail"), guest.read());
                assertEquals(0x22910506, ChannelFixtures.type(guest.read()));
                RoomIntegrationSupport.assertBalances(guest, 0, 0);
                RoomIntegrationSupport.assertBalances(foreign, 0, 0);
                owner.disconnect();

                assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), guest.read());
                assertArrayEquals(root("owner-transfer"), guest.read());
                assertEquals(1, lists.update(observer.read()).rooms().getFirst().playerCount());
                guest.disconnect();

                assertTrue(lists.initialize(observer.read()).rooms().isEmpty());
                RoomIntegrationSupport.assertBalances(observer, 0, 0);
                RoomIntegrationSupport.assertBalances(foreign, 0, 0);
                assertFalse(logs.text().contains("пароль"));
                assertFalse(logs.text().contains("Комната"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("bravo@example.test"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }
    }

    @Test
    void selectsTheMorePopulatedNoItemRoomInsteadOfTheFirstListedRoom() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("charlie");
        var d = player("delta");
        var config = database.configuration();
        var wire = new RoomResponseCodec();

        try (var app = new Application(config)) {
            app.start();
            try (
                var first = new CharacterTestClient(config.port());
                var second = new CharacterTestClient(config.port());
                var member = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                lobby(first, a, 1);
                first.send(root("create-unprotected"));
                int firstRoom = wire.initialize(first.read()).info().roomId();
                lobby(second, b, 1);
                byte[] noItem = integer(root("create-unprotected"), 31, 1, 2);
                noItem = integer(noItem, 33, 0, 1);
                second.send(noItem);
                int secondRoom = wire.initialize(second.read()).info().roomId();
                lobby(member, c, 1);
                member.send(RoomEntryFixtures.enter(secondRoom));
                wire.initialize(member.read());
                wire.slot(second.read());
                var listed = lobby(guest, d, 1);
                var roomIds = listed.rooms().stream().map(room -> room.roomId()).toList();

                assertEquals(List.of(firstRoom, secondRoom), roomIds);
                guest.send(root("quickjoin-any"));
                var joined = wire.initialize(guest.read());

                assertEquals(secondRoom, joined.info().roomId());
                assertEquals(1, joined.info().details().gameType());
                assertEquals(3, joined.info().details().playerCount());
                assertEquals(joined.slots().get(1), wire.slot(second.read()).slot());
                assertEquals(joined.slots().get(1), wire.slot(member.read()).slot());
                RoomIntegrationSupport.assertBalances(first, 0, 0);
            }
        }
    }

    @Test
    void racesQuickJoinAgainstEnterForTheLastTcpSeatWithoutOverwritingAParticipant() throws Exception {
        var accounts = new ArrayList<Account>();

        for (int i = 0; i < 9; i++) {
            accounts.add(player("bravo" + i));
        }

        var config = database.configuration();
        var clients = new ArrayList<CharacterTestClient>();
        var wire = new RoomResponseCodec();

        try (var app = new Application(config); var workers = Executors.newFixedThreadPool(2)) {
            app.start();
            try {
                var owner = new CharacterTestClient(config.port());
                clients.add(owner);
                lobby(owner, accounts.getFirst(), 1);
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();

                for (int i = 1; i < 9; i++) {
                    var client = new CharacterTestClient(config.port());
                    clients.add(client);
                    lobby(client, accounts.get(i), 1);

                    if (i < 7) {
                        client.send(RoomEntryFixtures.enter(room));
                        assertEquals(i + 1, wire.initialize(client.read()).info().details().playerCount());
                    }
                }

                clients.get(7).read();
                var quickJoin = workers.submit(
                    () -> {
                        clients.get(7).send(root("quickjoin-any"));
                        return entryReply(clients.get(7));
                    }
                );
                var enter = workers.submit(
                    () -> {
                        clients.get(8).send(RoomEntryFixtures.enter(room));
                        return entryReply(clients.get(8));
                    }
                );
                byte[] quickReply = quickJoin.get(10, TimeUnit.SECONDS);
                byte[] enterReply = enter.get(10, TimeUnit.SECONDS);
                boolean quickEntered = ChannelFixtures.type(quickReply) == 0x40a10704;
                byte[] accepted = quickEntered ? quickReply : enterReply;

                assertEquals(8, wire.initialize(accepted).info().details().playerCount());
                assertEquals(
                    8,
                    wire.initialize(accepted).slots().stream()
                    .map(slot -> slot.occupant().characterId()).distinct().count()
                );

                if (quickEntered) {
                    assertArrayEquals(root("enter-fail-5"), enterReply);
                    RoomIntegrationSupport.assertBalances(clients.get(8), 0, 0);
                } else {
                    assertArrayEquals(root("quickjoin-fail"), quickReply);
                    RoomIntegrationSupport.assertBalances(clients.get(7), 0, 0);
                }
            } finally {
                for (var client : clients) {
                    client.close();
                }
            }
        }
    }

    private byte[] entryReply(CharacterTestClient client) throws Exception {
        for (int i = 0; i < 12; i++) {
            byte[] packet = client.read();
            int type = ChannelFixtures.type(packet);

            if (type == 0x40a10704 || type == 0x6a8108f9 || type == 0x928e0a88) {
                return packet;
            }

            assertTrue(type == 0x611208a6 || type == 0x612308a8);
        }

        throw new AssertionError("Room entry reply was not received");
    }

    private Account player(String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        assertFalse(database.create(account.id(), "Hero" + name, AccountGender.FEMALE).catalog().isEmpty());
        return account;
    }

    private PcLobbyInitRoomList lobby(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        long character = database.characters().findByAccount(account.id()).getFirst().id().value();
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        return bootstrap(client);
    }

    private PcLobbyInitRoomList bootstrap(CharacterTestClient client) throws Exception {
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        var rooms = new LobbyRoomListCodec().initialize(client.read());
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
        return rooms;
    }
}
