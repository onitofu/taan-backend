package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.EOFException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.model.RoomMatchToken;

class MatchTerminationIntegrationTest {
    private static CharacterTestDatabase database;
    private final RoomResponseCodec wire = new RoomResponseCodec();

    @BeforeAll
    static void startDatabase() {
        database = new CharacterTestDatabase();
    }

    @AfterAll
    static void stopDatabase() {
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
    void endsActiveMatchWithoutChangingDurableDataAndAcceptsCardsAndAnotherReservation() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config); var logs = new LogCapture()) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                var rooms = prepare(app, owner, a, guest, b);
                var token = reserve(rooms, owner, guest, true);
                owner.send(MatchFixtures.root("give-up-empty"));
                ended(owner);
                ended(guest);
                owner.send(MatchFixtures.root("PqResult_RandSelect.captured"));
                owner.send(MatchFixtures.root("PqResult_SelectCard"));
                owner.send(MatchFixtures.root("give-up-empty"));
                cash(owner);
                assertEquals(before, durableFingerprint());
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                var next = reserve(rooms, owner, guest, true);

                assertFalse(token.equals(next));
                guest.send(MatchFixtures.root("give-up-empty"));
                ended(owner);
                ended(guest);
                assertEquals(before, durableFingerprint());
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains("alpha@example.test"));
                assertFalse(logs.text().contains("Heroalpha"));
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void closesLoadingConnectionsOnDisconnectAndReleasesTheirClaimsAndRoomNumber() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                var rooms = prepare(app, owner, a, guest, b);
                reserve(rooms, owner, guest, false);
                owner.disconnect();
                awaitClosed(guest);

                try (var fresh = new CharacterTestClient(config.port())) {
                    lobby(fresh, a);
                    fresh.send(RoomFixtures.root("create-unprotected"));
                    assertEquals(1, wire.initialize(fresh.read()).info().roomId());
                }
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void shutsDownLoadedMatchesWithinTheDeadlineAndRestartsWithOnlyDurableData() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                reserve(prepare(app, owner, a, guest, b), owner, guest, true);
                assertTimeoutPreemptively(Duration.ofSeconds(config.shutdownTimeoutSeconds()), app::close);
                app.close();
                awaitClosed(owner);
                awaitClosed(guest);
            }
        }

        try (var restarted = new Application(config)) {
            restarted.start();

            try (var client = new CharacterTestClient(config.port())) {
                lobby(client, a);
                cash(client);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    private RoomRegistry prepare(
        Application app,
        CharacterTestClient owner,
        Account a,
        CharacterTestClient guest,
        Account b
    ) throws Exception {
        lobby(owner, a);
        lobby(guest, b);
        owner.read();
        owner.send(RoomFixtures.root("create-unprotected"));
        int room = wire.initialize(owner.read()).info().roomId();
        guest.read();
        guest.read();
        guest.send(RoomEntryFixtures.enter(room));
        wire.initialize(guest.read());
        wire.slot(owner.read());
        guest.send(RoomScenario.ready(1));
        wire.ready(owner.read());
        wire.ready(guest.read());
        return RuntimeRoomTestAccess.rooms(app);
    }

    private RoomMatchToken reserve(
        RoomRegistry rooms,
        CharacterTestClient owner,
        CharacterTestClient guest,
        boolean loaded
    ) throws Exception {
        var ownerSession = RuntimeRoomTestAccess.room(rooms, 1).ownerSessionId();
        rooms.requestStart(ownerSession).delivery().toCompletableFuture().get(5, TimeUnit.SECONDS);
        var token = rooms.matchContext(ownerSession).token();

        for (var client : new CharacterTestClient[]{owner, guest}) {
            wire.slot(client.read());
            wire.slot(client.read());
            new StartGameCodec().decode(client.read());
        }

        if (loaded) {
            var matches = RuntimeRoomTestAccess.matches(rooms);
            MatchFixtures.loaded(matches, token);

            for (var client : new CharacterTestClient[]{owner, guest}) {
                new SetPosCodec().decode(client.read());
                new TurnStartCodec().decode(client.read());
            }

            assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    while (matches.current(token).orElseThrow().phase() != MatchPhase.ACTION) {
                        Thread.sleep(1);
                    }
                }
            );
        }

        return token;
    }

    private void ended(CharacterTestClient client) throws Exception {
        assertArrayEquals(MatchFixtures.root("end-game-result0-no-awards"), client.read());
        assertFalse(wire.ready(client.read()).ready());
    }

    private Account player(String name) {
        var account = database.account(name + "@example.test", AccountGender.FEMALE);
        assertFalse(database.create(account.id(), "Hero" + name, AccountGender.FEMALE).catalog().isEmpty());
        return account;
    }

    private void lobby(CharacterTestClient client, Account account) throws Exception {
        client.login(account.login().value());
        long character = database.characters().findByAccount(account.id()).getFirst().id().value();
        client.send(CharacterFixtures.select(character));
        client.read();
        client.read();
        client.send(ChannelFixtures.move(1));
        client.read();
        client.send(ChannelFixtures.enter(1));
        assertEquals(0x22b60517, ChannelFixtures.type(client.read()));
        client.read();
        client.read();
    }

    private void cash(CharacterTestClient client) throws Exception {
        client.send(SelectionFixtures.cash(0, 0));
        assertArrayEquals(SelectionFixtures.cash(0, 0), client.read());
    }

    private void awaitClosed(CharacterTestClient client) throws Exception {
        for (int index = 0; index < 16; index++) {
            try {
                client.read();
            } catch (EOFException | SocketException closed) {
                return;
            }
        }

        throw new AssertionError("Match connection remained active");
    }

    private String durableFingerprint() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");

        for (String table : new String[]{"accounts", "characters", "inventories", "inventory_items"}) {
            try (var connection = database.source().getConnection(); var query = connection.createStatement();
                var rows = query.executeQuery("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY id")) {
                while (rows.next()) {
                    digest.update(rows.getString(1).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}
