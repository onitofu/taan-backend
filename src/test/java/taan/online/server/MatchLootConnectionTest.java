package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.network.FrameDecoder;

class MatchLootConnectionTest {
    @Test
    void routesFragmentedAndCoalescedLootThroughFramingAndTheSharedDispatcherWithoutIssuance() throws Exception {
        try (var world = new RoomConnectionWorld(MatchExecutionMode.CANNON_1V1)) {
            var owner = world.client(1);
            var guest = world.client(2);
            world.drainOutputs();
            int room = world.create(owner);
            world.drainOutputs();
            world.join(guest, room);
            world.request(guest, RoomScenario.ready(1));
            world.request(owner, RoomFixtures.root("start-empty"));
            var token = world.rooms.matchContext(
                RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId()
            ).token();
            MatchFixtures.loaded(world.matches, token);
            world.drainOutputs();
            owner.channel().pipeline().addFirst(new FrameDecoder(16384, 10));

            try (var logs = new LogCapture()) {
                var before = world.matches.current(token).orElseThrow();
                byte[] pickup = frame(MatchFixtures.root("EatItemBox-plain.synthetic"));

                for (int offset = 0; offset < pickup.length; offset += 3) {
                    var fragment = Unpooled.wrappedBuffer(pickup, offset, Math.min(3, pickup.length - offset));
                    owner.channel().writeInbound(fragment);
                }

                var combined = new ByteArrayOutputStream();

                for (String name : List.of(
                    "notify-slot0-power-actor0.synthetic",
                    "forsake-known-slot1-actor0.synthetic",
                    "pending-03-PcGamePlay_EatBonusItem",
                    "notify-slot0-power-actor2.synthetic",
                    "forsake-known-slot1-actor2.synthetic"
                )) {
                    combined.write(frame(MatchFixtures.root(name)));
                }

                combined.write(frame(SelectionFixtures.cash(0, 0)));
                owner.channel().writeInbound(Unpooled.wrappedBuffer(combined.toByteArray()));

                while (!owner.jobs().isEmpty()) {
                    owner.run();
                    world.tick();
                }

                assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
                assertTrue(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertTrue(owner.channel().outboundMessages().isEmpty());
                assertTrue(guest.channel().outboundMessages().isEmpty());
                assertEquals(before, world.matches.current(token).orElseThrow());
                assertTrue(logs.text().contains("match.pickup.ignored"));
                assertTrue(logs.text().contains("match.item.receipt.ignored"));
                assertTrue(logs.text().contains("match.item.discard.ignored"));
                assertTrue(logs.text().contains("match.bonus.ignored"));
                assertTrue(logs.text().contains("INVALID_ITEM_CONTEXT"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains("member1@example.test"));
                assertFalse(logs.text().contains("PROTOCOL_ERROR"));
            }
        }
    }

    @Test
    void distinguishesIncomingPacketsFromServerOnlyRepliesAndKeepsFixtureHashes() throws Exception {
        String provenance;

        try (var input = getClass().getResourceAsStream("/match/loot-wire-provenance.json")) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String name : List.of(
            "EatItemBox-plain.synthetic",
            "EatItemBox-with-gauge.synthetic",
            "notify-slot0-power-actor0.synthetic",
            "notify-slot0-power-actor2.synthetic",
            "forsake-known-slot1-actor0.synthetic",
            "forsake-known-slot1-actor2.synthetic"
        )) {
            byte[] root = MatchFixtures.root(name);
            int type = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).getInt();
            assertTrue(MatchRequestDispatcher.accepts(type));
            assertEquals(ProtocolEvent.MATCH_REQUEST, RequestEvents.forType(type));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
            assertTrue(provenance.contains(hash));
        }

        assertFalse(MatchRequestDispatcher.accepts(0x70580923));
        assertFalse(MatchRequestDispatcher.accepts(0x12345678));
    }

    private byte[] frame(byte[] root) {
        return ByteBuffer.allocate(root.length + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(root.length).put(root).array();
    }
}
