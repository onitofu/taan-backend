package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

class RoomEntryIntegrationTest {
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
    void synchronizesRealRosterGuestDepartureOwnerDisconnectAndLobbyCountsOnlyWithinTheChannel() throws Exception {
        var a = player("alpha", AccountGender.MALE);
        var b = player("bravo", AccountGender.FEMALE);
        var c = player("observer", AccountGender.MALE);
        var d = player("foreign", AccountGender.MALE);
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var listings = new LobbyRoomListCodec();

        try (var app = new Application(config)) {
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
                database.execute("UPDATE characters SET level = 42 WHERE account_id = " + b.id().value());
                guest.sendTogether(RoomEntryFixtures.enter(room), SelectionFixtures.cash(0, 0));
                var initialized = wire.initialize(guest.read());
                var joined = wire.slot(owner.read());

                assertEquals(2, initialized.info().details().playerCount());
                assertEquals(character(a), initialized.option().ownerCharacterId());
                assertEquals(a.id().value(), initialized.option().ownerAccountId());
                assertEquals(character(b), joined.slot().occupant().characterId());
                assertEquals(42, joined.slot().occupant().level());
                assertEquals(2, joined.slot().occupant().appearance().mode());
                assertEquals(joined.slot(), initialized.slots().get(2));
                assertEquals(1, joined.resetWidget());
                assertArrayEquals(SelectionFixtures.cash(0, 0), guest.read());
                assertArrayEquals(SelectionFixtures.money(0), guest.read());
                assertEquals(List.of(character(b)), ChannelFixtures.removed(observer.read()));
                assertEquals(2, listings.update(observer.read()).rooms().getFirst().playerCount());
                guest.send(ChannelFixtures.leave());

                assertEquals(0x22910506, ChannelFixtures.type(guest.read()));
                foreign.send(RoomEntryFixtures.enter(room));
                assertEquals(4, wire.enterFail(foreign.read()).reason());
                guest.send(RoomScenario.leave(room, 0));

                assertEquals(1, wire.leave(guest.read()).success());
                assertEquals(0, wire.slot(owner.read()).slot().state());
                assertEquals(1, listings.update(observer.read()).rooms().getFirst().playerCount());
                guest.send(ChannelFixtures.enter(0));
                bootstrap(guest);
                observer.read();
                guest.send(RoomEntryFixtures.enter(room));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                observer.read();
                observer.read();
                owner.disconnect();
                var removed = wire.slot(guest.read());
                var transferred = wire.option(guest.read());

                assertEquals(0, removed.slot().slotIndex());
                assertEquals(0, removed.slot().state());
                assertEquals(character(b), transferred.option().ownerCharacterId());
                assertEquals(b.id().value(), transferred.option().ownerAccountId());
                assertEquals(1, listings.update(observer.read()).rooms().getFirst().playerCount());
                observer.send(RoomEntryFixtures.enter(room));
                var third = wire.initialize(observer.read());
                var added = wire.slot(guest.read());

                assertEquals(character(c), added.slot().occupant().characterId());
                assertEquals(0, added.slot().slotIndex());
                assertEquals(character(b), third.slots().get(2).occupant().characterId());
                assertEquals(character(b), third.option().ownerCharacterId());
                observer.send(RoomScenario.leave(room, 0));
                assertEquals(1, wire.leave(observer.read()).success());
                assertEquals(0, wire.slot(guest.read()).slot().state());
                guest.send(RoomScenario.leave(room, 0));
                assertEquals(1, wire.leave(guest.read()).success());
                observer.send(ChannelFixtures.enter(0));
                assertTrue(bootstrap(observer).rooms().isEmpty());
                cash(foreign);
                cash(guest);
            }
        }
    }

    @Test
    void admitsOneRealTcpClientToTheLastSeatAndKeepsTheRejectedConnectionUsable() throws Exception {
        var accounts = new ArrayList<Account>();
        for (int i = 0; i < 9; i++) {
            accounts.add(player("bravo" + i, AccountGender.MALE));
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
                var first = workers.submit(
                    () -> {
                        clients.get(7).send(RoomEntryFixtures.enter(room));
                        return entryReply(clients.get(7));
                    }
                );
                var second = workers.submit(
                    () -> {
                        clients.get(8).send(RoomEntryFixtures.enter(room));
                        return entryReply(clients.get(8));
                    }
                );
                var replies = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                var accepted = replies.stream().filter(value -> ChannelFixtures.type(value) == 0x40a10704).toList();
                var rejected = replies.stream().filter(value -> ChannelFixtures.type(value) == 0x6a8108f9).toList();

                assertEquals(1, accepted.size());
                assertEquals(1, rejected.size());
                assertEquals(8, wire.initialize(accepted.getFirst()).info().details().playerCount());
                assertEquals(5, wire.enterFail(rejected.getFirst()).reason());
                int loser = ChannelFixtures.type(replies.getFirst()) == 0x6a8108f9 ? 7 : 8;
                cash(clients.get(loser));
            } finally {
                for (var client : clients) {
                    client.close();
                }
            }
        }
    }

    private byte[] entryReply(CharacterTestClient client) throws Exception {
        for (int i = 0; i < 10; i++) {
            byte[] packet = client.read();
            int type = ChannelFixtures.type(packet);

            if (type == 0x40a10704 || type == 0x6a8108f9) {
                return packet;
            }

            assertTrue(type == 0x611208a6 || type == 0x612308a8);
        }

        throw new AssertionError("Room entry reply was not received");
    }

    private Account player(String name, AccountGender gender) {
        var account = database.account(name + "@example.test", gender);
        assertFalse(database.create(account.id(), "Hero" + name, gender).catalog().isEmpty());
        return account;
    }

    private long character(Account account) {
        return database.characters().findByAccount(account.id()).getFirst().id().value();
    }

    private void lobby(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        client.send(CharacterFixtures.select(character(account)));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));
        client.read();
        client.send(ChannelFixtures.enter(1));
        bootstrap(client);
    }

    private PcLobbyInitRoomList bootstrap(CharacterTestClient client) throws Exception {
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        var rooms = new LobbyRoomListCodec().initialize(client.read());
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
        return rooms;
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
    }
}
