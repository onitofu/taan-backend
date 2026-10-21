package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.GameSessionInfoPack;

class LobbyRoomListIntegrationTest {
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
    void publishesRealRoomsRemovesOnlyTheDepartedRoomAndIsolatesChannelSubscriptions() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("observer");
        var d = player("foreign");
        var config = database.configuration();
        var wire = new LobbyRoomListCodec();

        try (var app = new Application(config)) {
            app.start();
            try (
                var ownerA = new CharacterTestClient(config.port());
                var ownerB = new CharacterTestClient(config.port());
                var observer = new CharacterTestClient(config.port());
                var foreign = new CharacterTestClient(config.port())
            ) {
                enter(ownerA, a, 1);
                enter(ownerB, b, 1);
                ownerA.read();
                enter(observer, c, 1);
                ownerA.read();
                ownerB.read();
                enter(foreign, d, 2);
                ownerA.send(root("create-unprotected"));
                int roomA = new RoomResponseCodec().initialize(ownerA.read()).info().roomId();

                assertEquals(List.of(character(a)), ChannelFixtures.removed(ownerB.read()));
                assertEquals(List.of(roomA), ids(wire.update(ownerB.read()).rooms()));
                assertEquals(List.of(character(a)), ChannelFixtures.removed(observer.read()));
                assertEquals(List.of(roomA), ids(wire.update(observer.read()).rooms()));
                ownerB.send(root("create-unprotected"));
                int roomB = new RoomResponseCodec().initialize(ownerB.read()).info().roomId();

                assertNotEquals(roomA, roomB);
                assertEquals(List.of(character(b)), ChannelFixtures.removed(observer.read()));
                assertEquals(List.of(roomB), ids(wire.update(observer.read()).rooms()));
                observer.send(ChannelFixtures.enter(0));

                assertEquals(List.of(roomA, roomB), ids(bootstrap(observer)));
                observer.sendTogether(RoomEntryFixtures.enter(65535), SelectionFixtures.cash(0, 0));

                assertEquals(4, new RoomResponseCodec().enterFail(observer.read()).reason());
                assertArrayEquals(SelectionFixtures.cash(0, 0), observer.read());
                assertArrayEquals(SelectionFixtures.money(0), observer.read());
                ownerA.disconnect();

                assertEquals(List.of(roomB), ids(wire.initialize(observer.read()).rooms()));
                cash(foreign);
                observer.send(ChannelFixtures.move(2));
                observer.read();
                observer.send(ChannelFixtures.enter(1));

                assertTrue(bootstrap(observer).isEmpty());
                foreign.read();
                ownerB.send(RoomScenario.leave(roomB, 0));

                assertEquals(1, new RoomResponseCodec().leave(ownerB.read()).success());
                cash(observer);
                cash(foreign);
                observer.send(ChannelFixtures.move(1));
                observer.read();
                foreign.read();
                observer.send(ChannelFixtures.enter(1));

                assertTrue(bootstrap(observer).isEmpty());
                ownerB.send(ChannelFixtures.enter(0));

                assertTrue(bootstrap(ownerB).isEmpty());
                observer.read();
                ownerB.send(ChannelFixtures.enter(0));

                assertTrue(bootstrap(ownerB).isEmpty());
                cash(observer);
                cash(foreign);
            }
        }
    }

    @Test
    void deliversAllLongTitlesAcrossFramesAndResetsToTheRemainingRoomsAfterDeletion() throws Exception {
        var config = database.configuration();
        var owners = new ArrayList<CharacterTestClient>();
        var observerAccount = player("reader");
        var accounts = new ArrayList<Account>();
        for (int i = 0; i < 31; i++) {
            accounts.add(player("owner" + i));
        }

        try (var app = new Application(config)) {
            app.start();
            try {
                for (var account : accounts) {
                    var owner = new CharacterTestClient(config.port());
                    owners.add(owner);
                    enter(owner, account, 1);
                    owner.sendFragmented(RoomListingFixtures.create("界".repeat(256)));

                    assertEquals(owners.size(), new RoomResponseCodec().initialize(owner.read()).info().roomId());
                }

                try (var observer = new CharacterTestClient(config.port())) {
                    var rooms = enter(observer, observerAccount, 1);

                    assertEquals(IntStream.rangeClosed(1, 31).boxed().toList(), ids(rooms));
                    assertTrue(rooms.stream().allMatch(room -> room.title().equals("界".repeat(256))));
                    assertTrue(rooms.stream().allMatch(room -> room.password() == null && room.playerCount() == 1));
                    owners.getFirst().send(RoomScenario.leave(1, 0));

                    assertEquals(1, new RoomResponseCodec().leave(owners.getFirst().read()).success());
                    assertEquals(
                        IntStream.rangeClosed(2, 31).boxed().toList(),
                        ids(new LobbyRoomListCodec().initialize(observer.read()).rooms())
                    );
                    cash(observer);
                }
            } finally {
                for (var owner : owners) {
                    owner.close();
                }
            }
        }
    }

    private Account player(String name) {
        var account = database.account(name + "@example.test", AccountGender.MALE);
        database.create(account.id(), "Hero" + name, AccountGender.MALE);
        return account;
    }

    private long character(Account account) {
        return database.characters().findByAccount(account.id()).getFirst().id().value();
    }

    private List<GameSessionInfoPack> enter(CharacterTestClient client, Account account, int channel) throws Exception {
        client.login(account.login().value());
        client.send(CharacterFixtures.select(character(account)));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(channel));

        assertEquals(0x33cf0634, ChannelFixtures.type(client.read()));
        client.send(ChannelFixtures.enter(1));
        return bootstrap(client);
    }

    private List<GameSessionInfoPack> bootstrap(CharacterTestClient client) throws Exception {
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        var wire = new LobbyRoomListCodec();
        var result = new ArrayList<>(wire.initialize(client.read()).rooms());
        byte[] next = client.read();
        while (ChannelFixtures.type(next) == 0x611208a6) {
            result.addAll(wire.update(next).rooms());
            next = client.read();
        }

        assertEquals(0x501d07d9, ChannelFixtures.type(next));
        return List.copyOf(result);
    }

    private List<Integer> ids(List<GameSessionInfoPack> rooms) {
        return rooms.stream().map(GameSessionInfoPack::roomId).toList();
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));

        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
        assertArrayEquals(SelectionFixtures.money(0), client.read());
    }
}
