package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
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

class RoomPasswordIntegrationTest {
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
    void retriesAfterPasswordFailuresAndPreservesProtectionAndChannelIsolationAfterOwnerDisconnect() throws Exception {
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
                owner.sendFragmented(root("create-password"));
                byte[] created = owner.read();
                var initial = wire.initialize(created);
                int room = initial.info().roomId();
                guest.read();
                byte[] listed = guest.read();
                observer.read();
                observer.read();

                assertEquals("", initial.info().details().password());
                assertEquals("", lists.update(listed).rooms().getFirst().password());
                assertArrayEquals(
                    new byte[]{1, 0, 0, 0, 0},
                    Arrays.copyOfRange(listed, listed.length - 5, listed.length)
                );
                assertNoSecret(created, "пароль");
                assertNoSecret(listed, "пароль");
                guest.sendTogether(RoomEntryFixtures.enter(room), RoomEntryFixtures.enter(room, "wrong"));

                assertArrayEquals(root("enter-fail-6"), guest.read());
                assertArrayEquals(root("enter-fail-6"), guest.read());
                cash(guest);
                cash(owner);
                cash(observer);
                foreign.send(RoomEntryFixtures.enter(room, "пароль"));

                assertArrayEquals(root("enter-fail-4"), foreign.read());
                cash(foreign);
                guest.sendFragmented(RoomEntryFixtures.enter(room, "пароль"));
                byte[] joined = guest.read();

                assertEquals("", wire.initialize(joined).info().details().password());
                assertEquals(2, wire.initialize(joined).info().details().playerCount());
                assertNoSecret(joined, "пароль");
                wire.slot(owner.read());
                observer.read();
                assertEquals(2, lists.update(observer.read()).rooms().getFirst().playerCount());
                owner.send(RoomEntryFixtures.enter(room, "пароль"));

                assertArrayEquals(root("enter-fail-3"), owner.read());
                owner.disconnect();
                assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), guest.read());
                assertArrayEquals(root("owner-transfer"), guest.read());
                assertEquals("", lists.update(observer.read()).rooms().getFirst().password());
                observer.send(RoomEntryFixtures.enter(room, ""));

                assertArrayEquals(root("enter-fail-6"), observer.read());
                guest.send(RoomScenario.leave(room, 0));
                assertEquals(1, wire.leave(guest.read()).success());
                assertTrue(lists.initialize(observer.read()).rooms().isEmpty());
                observer.send(RoomEntryFixtures.enter(room, "пароль"));

                assertArrayEquals(root("enter-fail-4"), observer.read());
                cash(observer);
                cash(foreign);
                assertFalse(logs.text().contains("пароль"));
                assertFalse(logs.text().contains("wrong"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains("alpha@example.test"));
                assertFalse(logs.text().contains("Комната"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            }
        }
    }

    @Test
    void rejectsOutOfBoundsCreationAndTruncatedPasswordsWithoutChangingTheOwnersRoom() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var config = database.configuration();
        var wire = new RoomResponseCodec();
        var secret = "😀".repeat(5);

        try (var app = new Application(config); var logs = new LogCapture()) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                lobby(owner, a, 1);
                lobby(guest, b, 1);
                owner.read();
                owner.sendTogether(
                    RoomListingFixtures.create("Room", secret + "x"),
                    RoomListingFixtures.create("x".repeat(257), secret)
                );

                assertArrayEquals(root("create-fail-generic"), owner.read());
                assertArrayEquals(root("create-fail-generic"), owner.read());
                owner.send(RoomListingFixtures.create("😀".repeat(128), secret));
                var initialized = wire.initialize(owner.read());
                int room = initialized.info().roomId();
                guest.read();
                guest.read();

                assertEquals(256, initialized.info().details().title().length());
                assertEquals("", initialized.info().details().password());
                guest.send(RoomEntryFixtures.enter(room, secret));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                guest.send(RoomScenario.leave(room, 0));
                wire.leave(guest.read());
                wire.slot(owner.read());
                guest.send(ChannelFixtures.enter(0));
                bootstrap(guest);
                byte[] valid = RoomEntryFixtures.enter(room, secret);
                guest.send(Arrays.copyOf(valid, 11));
                guest.awaitClosure();
                cash(owner);

                assertFalse(logs.text().contains(secret));
                assertFalse(logs.text().contains(secret + "x"));
                assertTrue(logs.text().contains("protocol.frame.rejected"));
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
    }

    private void assertNoSecret(byte[] root, String password) {
        assertFalse(
            HexFormat.of().formatHex(root).contains(
                HexFormat.of().formatHex(password.getBytes(StandardCharsets.UTF_16LE))
            )
        );
    }
}
