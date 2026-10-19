package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.MoveCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.protocol.BinaryWriter;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.bootstrap.Application;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.RoomRegistry;

class MatchBootstrapIntegrationTest {
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
        database.execute("UPDATE channels SET kind = 0");
        database.execute("ALTER SEQUENCE characters_id_seq RESTART WITH 1001");
    }

    @Test
    void bootsBothTcpClientsAndReturnsThemWithoutChangingPersistentData() throws Exception {
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
                prepare(app, owner, a, guest, b);
                owner.sendFragmented(RoomFixtures.root("start-empty"));
                var start = started(owner);
                assertEquals(start, started(guest));
                owner.sendTogether(
                    MatchFixtures.root("native-policy-v4-report-enemies-0"),
                    MatchFixtures.root("native-policy-v4-loading-1")
                );
                guest.sendTogether(
                    MatchFixtures.root("native-policy-v4-report-enemies-1"),
                    MatchFixtures.root("native-policy-v4-loading-0")
                );
                var ownerPose = new SetPosCodec().decode(owner.read());
                var guestPose = new SetPosCodec().decode(guest.read());
                var first = new TurnStartCodec().decode(owner.read());
                assertEquals(first, new TurnStartCodec().decode(guest.read()));
                assertEquals(2, ownerPose.slotIndex());
                assertEquals(0, guestPose.slotIndex());
                assertTrue(start.players().stream().anyMatch(player -> player.slotIndex() == first.activeSlot()));
                assertEquals(0, first.energyGrant());
                assertEquals(start.initialWindRaw(), first.windRawTenths());
                assertEquals(0, first.boxAttemptBudget());
                owner.sendTogether(new byte[]{0x6d, 0x08, 0x0b, 0x5e, 0, 0}, new SetPosCodec().encode(guestPose));
                cash(owner);
                owner.send(MatchFixtures.root("give-up-empty"));
                ended(owner);
                ended(guest);
                assertEquals(before, durableFingerprint());
                guest.send(RoomScenario.ready(1));
                wire.ready(owner.read());
                wire.ready(guest.read());
                owner.send(RoomFixtures.root("start-empty"));
                started(owner);
                started(guest);
                owner.disconnect();
                awaitClosed(guest);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void expiresUnfinishedLoadingAndReleasesTheAccountClaims() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration(Map.of("BACKEND_MATCH_LOADING_TIMEOUT_SECONDS", "1"));

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                prepare(app, owner, a, guest, b);
                owner.send(RoomFixtures.root("start-empty"));
                started(owner);
                started(guest);
                awaitClosed(owner);
                awaitClosed(guest);

                try (var fresh = new CharacterTestClient(config.port())) {
                    lobby(fresh, a);
                    cash(fresh);
                }
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void watchdogStopsAndPassesAnInactiveActionWithoutChangingPersistentData() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration(Map.of("BACKEND_MATCH_ACTION_TIMEOUT_SECONDS", "1"));

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                prepare(app, owner, a, guest, b);
                owner.send(RoomFixtures.root("start-empty"));
                started(owner);
                started(guest);
                owner.send(MatchFixtures.root("native-policy-v4-loading-1"));
                guest.send(MatchFixtures.root("native-policy-v4-loading-0"));
                new SetPosCodec().decode(owner.read());
                new SetPosCodec().decode(guest.read());
                var first = new TurnStartCodec().decode(owner.read());
                assertEquals(first, new TurnStartCodec().decode(guest.read()));
                assertEquals(first.activeSlot(), new TurnEndCodec().decode(owner.read()).slotIndex());
                assertEquals(first.activeSlot(), new TurnEndCodec().decode(guest.read()).slotIndex());
                var observer = first.activeSlot() == 0 ? guest : owner;
                assertEquals(first.activeSlot(), new SetPosCodec().decode(observer.read()).slotIndex());
                var next = new TurnStartCodec().decode(owner.read());
                assertEquals(next, new TurnStartCodec().decode(guest.read()));
                assertEquals(first.activeSlot() == 0 ? 2 : 0, next.activeSlot());
                owner.send(MatchFixtures.root("give-up-empty"));
                ended(owner);
                ended(guest);
                cash(owner);
                cash(guest);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void loadsAllOrdinaryMapsOverTwoTcpConnectionsAndReturnsToTheSameRoom() throws Exception {
        database.execute("UPDATE channels SET kind = 6 WHERE id = 1");
        var a = player("alpha");
        var b = player("bravo");
        var catalog = MatchFixtures.catalog();
        String before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                prepare(app, owner, a, guest, b);

                for (int mapId = 1; mapId <= 21; mapId++) {
                    owner.send(RoomScenario.map(mapId));
                    assertEquals(mapId, wire.settings(owner.read()).mapId());
                    assertEquals(mapId, wire.settings(guest.read()).mapId());
                    owner.send(RoomFixtures.root("start-empty"));
                    var start = started(owner, mapId);
                    assertEquals(start, started(guest, mapId));
                    var map = catalog.maps().get(mapId);

                    for (var actor : start.players()) {
                        var spawn = map.spawns().stream().filter(
                            value -> value.rawX() == actor.x() && value.rawY() == actor.y()
                        ).findFirst().orElseThrow();
                        byte[] acknowledgement = new LoadingDoneCodec().encode(
                            new PcGamePlayLoadingDone(
                                List.of(new LoadingPosition(actor.slotIndex(), spawn.settledX(), spawn.settledY()))
                            )
                        );
                        (actor.slotIndex() == 0 ? owner : guest).send(acknowledgement);
                    }

                    assertEquals(2, new SetPosCodec().decode(owner.read()).slotIndex());
                    assertEquals(0, new SetPosCodec().decode(guest.read()).slotIndex());
                    assertEquals(
                        new TurnStartCodec().decode(owner.read()),
                        new TurnStartCodec().decode(guest.read())
                    );
                    owner.send(MatchFixtures.root("give-up-empty"));
                    ended(owner);
                    ended(guest);

                    if (mapId < 21) {
                        guest.send(RoomScenario.ready(1));
                        wire.ready(owner.read());
                        wire.ready(guest.read());
                    }
                }

                cash(owner);
                cash(guest);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void passesFromTheAirAndFromTheGroundWithoutEchoingTheOwnersMotion() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();

            try (var host = new CharacterTestClient(config.port()); var peer = new CharacterTestClient(config.port())) {
                prepare(app, host, a, peer, b);
                host.send(RoomFixtures.root("start-empty"));
                var start = started(host);
                started(peer);
                host.send(MatchFixtures.root("native-policy-v4-loading-1"));
                peer.send(MatchFixtures.root("native-policy-v4-loading-0"));
                new SetPosCodec().decode(host.read());
                new SetPosCodec().decode(peer.read());
                var turn = new TurnStartCodec().decode(host.read());
                assertEquals(turn, new TurnStartCodec().decode(peer.read()));

                for (int count = 0; count < 2; count++) {
                    int slot = turn.activeSlot();
                    var active = slot == 0 ? host : peer;
                    var observer = slot == 0 ? peer : host;
                    var player = start.players().stream().filter(value -> value.slotIndex() == slot)
                        .findFirst().orElseThrow();
                    var spawn = MatchFixtures.definitions().map().spawns().stream()
                        .filter(value -> value.rawX() == player.x() && value.rawY() == player.y())
                        .findFirst().orElseThrow();
                    int y = spawn.settledY() - (count == 0 ? 20 : 0);
                    var pass = new PcGamePlayPassTurn(slot, spawn.settledX(), y, player.facing(), 0);
                    active.sendFragmented(new PassTurnCodec().encode(pass));
                    assertEquals(pass, new PassTurnCodec().decode(observer.read()));
                    assertEquals(spawn.settledY(), new SetPosCodec().decode(observer.read()).y());
                    assertEquals(slot, new TurnEndCodec().decode(active.read()).slotIndex());
                    assertEquals(slot, new TurnEndCodec().decode(observer.read()).slotIndex());
                    turn = new TurnStartCodec().decode(host.read());
                    assertEquals(turn, new TurnStartCodec().decode(peer.read()));
                    assertEquals(slot == 0 ? 2 : 0, turn.activeSlot());
                }

                host.send(MatchFixtures.root("give-up-empty"));
                ended(host);
                ended(peer);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    @Test
    void exchangesThreeScenariosThroughProductionDispatchAndAcknowledgesEachSessionOnce() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        var before = durableFingerprint();
        var config = database.configuration();

        try (var app = new Application(config)) {
            app.start();

            try (
                var owner = new CharacterTestClient(config.port());
                var guest = new CharacterTestClient(config.port())
            ) {
                prepare(app, owner, a, guest, b);
                owner.send(RoomFixtures.root("start-empty"));
                var start = started(owner);
                assertEquals(start, started(guest));
                owner.send(MatchFixtures.root("native-policy-v4-loading-1"));
                guest.send(MatchFixtures.root("native-policy-v4-loading-0"));
                new SetPosCodec().decode(owner.read());
                new SetPosCodec().decode(guest.read());
                var turn = new TurnStartCodec().decode(owner.read());
                assertEquals(turn, new TurnStartCodec().decode(guest.read()));
                assertEquals(start.initialWindRaw(), turn.windRawTenths());

                for (int index = 0; index < 3; index++) {
                    int active = turn.activeSlot();
                    var shooter = active == 0 ? owner : guest;
                    var observer = active == 0 ? guest : owner;
                    var shot = MatchScenarioTestPackets.shot(active, active == 0 ? 0 : 1, 10, false);
                    byte[] root = new ScenarioCodec().encode(shot);
                    byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                        .unsignedByte(active).unsignedByte(0).toByteArray();

                    if (index == 1) {
                        shooter.sendFragmented(root);
                        shooter.sendTogether(ack, ack);
                    } else {
                        if (index == 0) {
                            var pose = start.players().stream().filter(player -> player.slotIndex() == active)
                                .findFirst().orElseThrow();
                            var spawn = MatchFixtures.definitions().map().spawns().stream()
                                .filter(value -> value.rawX() == pose.x() && value.rawY() == pose.y())
                                .findFirst().orElseThrow();
                            var move = new PcGamePlayMove(
                                active,
                                spawn.settledX() + 1,
                                spawn.settledY(),
                                pose.facing()
                            );
                            shooter.sendTogether(new MoveCodec().encode(move), root, ack);
                            assertEquals(move, new MoveCodec().decode(observer.read()));
                        } else {
                            shooter.sendTogether(root, ack);
                        }

                        shooter.send(ack);
                    }

                    assertArrayEquals(root, observer.read());
                    cash(shooter);
                    observer.sendTogether(ack, ack);
                    assertEquals(active, new TurnEndCodec().decode(owner.read()).slotIndex());
                    assertEquals(active, new TurnEndCodec().decode(guest.read()).slotIndex());
                    var next = new TurnStartCodec().decode(owner.read());
                    assertEquals(next, new TurnStartCodec().decode(guest.read()));
                    assertEquals(active == 0 ? 2 : 0, next.activeSlot());
                    assertEquals(0, next.energyGrant());
                    assertEquals(0, next.boxAttemptBudget());
                    assertTrue(Math.abs(next.windRawTenths() - turn.windRawTenths()) <= 20);

                    if (index != 1) {
                        assertEquals(turn.windRawTenths(), next.windRawTenths());
                    }

                    turn = next;
                }

                owner.send(MatchFixtures.root("give-up-empty"));
                ended(owner);
                ended(guest);
                assertEquals(before, durableFingerprint());
            }
        }
    }

    private PcGameSessionStartGame started(CharacterTestClient client) throws Exception {
        return started(client, 1);
    }

    private PcGameSessionStartGame started(CharacterTestClient client, int mapId) throws Exception {
        for (int index = 0; index < 2; index++) {
            var slot = wire.slot(client.read()).slot();
            assertEquals(index == 0 ? 0 : 2, slot.slotIndex());
            assertTrue(slot.occupant().options() != null);
            assertEquals(1001 + index, slot.occupant().characterId());
            assertEquals(0, slot.occupant().weaponId());
        }

        byte[] root = client.read();
        assertEquals(91, root.length);
        var start = new StartGameCodec().decode(root);
        assertEquals(mapId, start.mapId());
        assertTrue(
            start.players().stream().allMatch(member -> member.initialEnergyGrant() == 0 && member.weaponId() == 0)
        );
        return start;
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
