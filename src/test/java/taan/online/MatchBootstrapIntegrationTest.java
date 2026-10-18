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
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
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
                assertEquals(0, first.windRawTenths());
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
    void automaticallyEndsThePreviewWithoutStartingASecondTurn() throws Exception {
        var a = player("alpha");
        var b = player("bravo");
        String before = durableFingerprint();
        var config = database.configuration(Map.of("BACKEND_MATCH_PREVIEW_TIMEOUT_SECONDS", "1"));

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
                ended(owner);
                ended(guest);
                cash(owner);
                cash(guest);
            }
        }

        assertEquals(before, durableFingerprint());
    }

    private PcGameSessionStartGame started(CharacterTestClient client) throws Exception {
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
        assertEquals(1, start.mapId());
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
