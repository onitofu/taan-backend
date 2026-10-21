package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.protocol.BinaryWriter;
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
    void endsActiveMatchAndAcceptsOnePrepaidCardChoiceBeforeAnotherReservation() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = database.unrelatedDataFingerprint(true);
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
                ended(owner, 2);
                ended(guest, 2);
                var cards = new ResultSelectionCodec();
                owner.send(cards.encode(new PqResultSelectCard(3, 0)));
                assertEquals(3, cards.selected(owner.read()).cardIndex());
                assertEquals(0, cards.selected(guest.read()).slotIndex());
                owner.send(cards.encode(new PqResultSelectCard(5, 0)));
                assertEquals(3, cards.selected(owner.read()).cardIndex());
                owner.send(MatchFixtures.root("give-up-empty"));
                RoomIntegrationSupport.assertBalances(database, owner, a);
                assertEquals(before, database.unrelatedDataFingerprint(true));
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                var next = reserve(rooms, owner, guest, true);

                assertFalse(token.equals(next));
                guest.send(MatchFixtures.root("give-up-empty"));
                ended(owner, 1);
                ended(guest, 1);
                assertEquals(before, database.unrelatedDataFingerprint(true));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains("alpha@example.test"));
                assertFalse(logs.text().contains("Heroalpha"));
            }
        }

        assertEquals(before, database.unrelatedDataFingerprint(true));
    }

    @Test
    void closesLoadingConnectionsOnDisconnectAndReleasesTheirClaimsAndRoomNumber() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = database.unrelatedDataFingerprint();
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

        assertEquals(before, database.unrelatedDataFingerprint());
    }

    @Test
    void shutsDownLoadedMatchesWithinTheDeadlineAndRestartsWithOnlyDurableData() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = database.unrelatedDataFingerprint();
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
                RoomIntegrationSupport.assertBalances(database, client, a);
            }
        }

        assertEquals(before, database.unrelatedDataFingerprint());
    }

    @Test
    void commitsTheDamageMedalBeforeTcpResultsAndPlaysAnotherMatchWithoutOtherPersistentChanges() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = database.unrelatedDataFingerprint(true);
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();
            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                var rooms = prepare(app, owner, a, guest, b);
                var matches = RuntimeRoomTestAccess.matches(rooms);
                var token = reserve(rooms, owner, guest, true);
                var snapshot = matches.current(token).orElseThrow();
                int slot = snapshot.turn().activeSlot();
                var actor = snapshot.participants().stream().filter(p -> p.slotIndex() == slot)
                    .findFirst().orElseThrow();
                var shooter = slot == 0 ? owner : guest;
                var observer = slot == 0 ? guest : owner;
                var packet = MatchScenarioTestPackets.shot(
                    slot,
                    snapshot.actors().get(actor.sessionId()).facing(),
                    10000,
                    false
                );
                byte[] root = new ScenarioCodec().encode(packet);
                byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                    .unsignedByte(slot).unsignedByte(0).toByteArray();
                shooter.sendTogether(root, ack);
                assertArrayEquals(root, observer.read());
                observer.sendTogether(ack, ack);
                int winner = slot == 0 ? 1 : 2;
                ended(owner, winner);
                ended(guest, winner);
                long awarded = database.scalar(
                    "SELECT force_medals FROM characters WHERE id = " + actor.character().id().value()
                );
                assertEquals(1, awarded);
                assertEquals(1, database.scalar("SELECT sum(force_medals) FROM characters"));
                RoomIntegrationSupport.assertBalances(database, owner, a);
                RoomIntegrationSupport.assertBalances(database, guest, b);
                assertEquals(before, database.unrelatedDataFingerprint(true));
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                var next = reserve(rooms, owner, guest, true);
                assertFalse(next.equals(token));
                owner.send(MatchFixtures.root("give-up-empty"));
                ended(owner, 2);
                ended(guest, 2);
                assertEquals(1, database.scalar("SELECT sum(force_medals) FROM characters"));
                assertEquals(before, database.unrelatedDataFingerprint(true));
            }
        }
    }

    @Test
    void keepsTheCommittedWinnerWhenTheDefeatedTcpPeerDoesNotAcknowledge() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = database.unrelatedDataFingerprint(true);
        var config = database.configuration(Map.of("BACKEND_MATCH_COMPLETION_GRACE_SECONDS", "1"));

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                var rooms = prepare(app, owner, a, guest, b);
                var token = reserve(rooms, owner, guest, true);
                var matches = RuntimeRoomTestAccess.matches(rooms);
                var snapshot = matches.current(token).orElseThrow();
                int slot = snapshot.turn().activeSlot();
                var shooter = snapshot.participants().stream().filter(p -> p.slotIndex() == slot)
                    .findFirst().orElseThrow();
                var defeated = snapshot.participants().stream().filter(p -> p.slotIndex() != slot)
                    .findFirst().orElseThrow();
                var winner = slot == 0 ? owner : guest;
                var loser = slot == 0 ? guest : owner;
                byte[] root = new ScenarioCodec().encode(MatchScenarioTestPackets.shot(
                    slot,
                    snapshot.actors().get(shooter.sessionId()).facing(),
                    10000,
                    false
                ));
                byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                    .unsignedByte(slot).unsignedByte(0).toByteArray();
                winner.sendTogether(root, ack);
                assertArrayEquals(root, loser.read());

                ended(winner, slot == 0 ? 1 : 2);
                awaitClosed(loser);
                assertEquals(defeated.slotIndex(), wire.slot(winner.read()).slot().slotIndex());
                wire.option(winner.read());
                RoomIntegrationSupport.assertBalances(database, winner, slot == 0 ? a : b);
                assertTrue(matches.current(token).isEmpty());
                assertTrue(rooms.membership(defeated.sessionId()).isEmpty());
                assertEquals(shooter.sessionId(), RuntimeRoomTestAccess.room(rooms, 1).ownerSessionId());
                assertEquals(before, database.unrelatedDataFingerprint(true));
            }
        }
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

    private void ended(CharacterTestClient client, int winner) throws Exception {
        MatchResultAssertions.readEnd(client::read, winner);
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
}
