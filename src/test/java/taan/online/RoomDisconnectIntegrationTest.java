package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;

import java.io.EOFException;
import java.net.SocketException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.lobby.protocol.codec.LobbyRoomListCodec;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomDisconnectIntegrationTest {
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
    void preservesTheRoomWhenTheOwnerLeavesNormally() throws Exception {
        handoff("LEAVE");
    }

    @Test
    void preservesTheRoomWhenTheOwnerClosesTcpWithoutALeavePacket() throws Exception {
        handoff("EOF");
    }

    @Test
    void preservesTheRoomWhenTheOwnersSocketResets() throws Exception {
        handoff("RST");
    }

    @Test
    void closesOccupiedRoomsAndConnectionsAndRestartsWithDurableCharactersAndNoRooms() throws Exception {
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
                owner.send(root("create-password"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                guest.send(RoomEntryFixtures.enter(room, "пароль"));
                wire.initialize(guest.read());
                wire.slot(owner.read());
                assertTimeoutPreemptively(Duration.ofSeconds(config.shutdownTimeoutSeconds()), app::close);
                app.close();
                awaitClosed(owner);
                awaitClosed(guest);
            }
        }

        assertEquals(2, database.scalar("SELECT COUNT(*) FROM characters"));
        try (var restarted = new Application(config)) {
            restarted.start();
            try (var client = new CharacterTestClient(config.port())) {
                lobby(client, a, 1);
                cash(client);
            }
        }
    }

    private void handoff(String mode) throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var c = player("reader");
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
                owner.send(root("create-unprotected"));
                int room = wire.initialize(owner.read()).info().roomId();
                guest.read();
                guest.read();
                observer.read();
                observer.read();
                guest.send(RoomEntryFixtures.enter(room));
                var initialized = wire.initialize(guest.read());
                wire.slot(owner.read());
                observer.read();
                observer.read();

                assertEquals(2, initialized.slots().get(2).state());
                switch (mode) {
                    case "LEAVE" -> {
                        owner.send(RoomScenario.leave(room, 0));
                        assertEquals(1, wire.leave(owner.read()).success());
                    }
                    case "EOF" -> owner.disconnect();
                    default -> owner.resetConnection();
                }

                assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), guest.read());
                assertArrayEquals(root("owner-transfer"), guest.read());
                assertEquals(1, lists.update(observer.read()).rooms().getFirst().playerCount());
                guest.send(root("start-empty"));
                assertEquals(
                    "Starting a match is currently unavailable.",
                    new NoticeMessageCodec().decode(guest.read()).message()
                );
                cash(foreign);
                guest.send(RoomScenario.leave(room, 0));
                assertEquals(1, wire.leave(guest.read()).success());
                assertTrue(lists.initialize(observer.read()).rooms().isEmpty());
                guest.send(ChannelFixtures.enter(0));
                bootstrap(guest);
                observer.read();
                cash(guest);
                cash(foreign);
                assertEquals(1, count(logs, "room.owner.changed"));
                assertEquals(1, count(logs, "room.destroyed"));
                assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
                assertFalse(logs.text().contains("alpha@example.test"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("Ngon vào đây"));
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

    private void bootstrap(CharacterTestClient client) throws Exception {
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        assertTrue(new LobbyRoomListCodec().initialize(client.read()).rooms().isEmpty());
        assertEquals(0x501d07d9, ChannelFixtures.type(client.read()));
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
    }

    private void awaitClosed(CharacterTestClient client) throws Exception {
        for (int i = 0; i < 16; i++) {
            try {
                client.read();
            } catch (EOFException | SocketException closed) {
                return;
            }
        }

        throw new AssertionError("Connection remained active after shutdown");
    }

    private long count(LogCapture logs, String name) {
        return logs.events().stream().filter(
            event -> event.getKeyValuePairs().stream().anyMatch(
                field -> field.key.equals("event") && field.value.equals(name)
            )
        ).count();
    }
}
