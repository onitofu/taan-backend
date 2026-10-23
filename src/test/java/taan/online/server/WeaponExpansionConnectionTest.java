package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.WeaponExpansionCase;
import taan.online.WeaponExpansionFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;

class WeaponExpansionConnectionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("baselines")
    void routesNativeAttacksAndCoalescedSpendingAfterStreamFragmentation(
        WeaponExpansionCase evidence
    ) throws Exception {
        try (var f = new ChannelLootConnectionFixture(100, 100, 2); var logs = new LogCapture()) {
            var token = f.start(1, false, evidence.weaponId(), 100);

            if (evidence.attackKind() == 2) {
                for (int turn = 0; turn < 100; turn++) {
                    f.pass(token);
                }
            }

            while (f.active(token).slotIndex() != 0) {
                f.pass(token);
            }

            var shooter = f.active(token);
            var sender = f.client(shooter);

            if (evidence.attackKind() == 1) {
                f.pass(token);
                var bonus = f.bonus(shooter, 5);
                f.world.request(sender, bonus);
                f.expectBoth(token, bonus);
                f.confirm(shooter, 0, 5);
                f.pass(token);
            }

            if (evidence.attackKind() != 0) {
                byte[] use = new UseItemCodec().encode(new PcGamePlayUseItem(
                    0,
                    1,
                    0,
                    0,
                    evidence.attackKind() == 2 ? 255 : 0,
                    evidence.attackKind() == 2 ? 6 : 5
                ));
                f.world.request(sender, use);
                f.expectBoth(token, use);
            }

            var before = f.snapshot(token);
            byte[] root = WeaponExpansionFixtures.root(evidence);
            byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(0, 1, 255, List.of()));
            var stream = Unpooled.buffer();
            sender.channel().pipeline().addFirst(new FrameDecoder(65536, 10));

            try {
                if (evidence.attackKind() != 0) {
                    stream.writeIntLE(spend.length).writeBytes(spend);
                }

                stream.writeIntLE(root.length).writeBytes(root).writeIntLE(root.length).writeBytes(root);
                int fragment = 1;

                while (stream.isReadable()) {
                    sender.channel().writeInbound(stream.readRetainedSlice(Math.min(fragment, stream.readableBytes())));
                    fragment *= 2;
                }

                while (!sender.jobs().isEmpty()) {
                    sender.run();
                    f.world.tick();
                }
            } finally {
                stream.release();
                sender.channel().pipeline().remove(FrameDecoder.class);
            }

            assertEquals(before.world().revision() + 1, f.snapshot(token).world().revision());
            assertEquals(1, f.snapshot(token).world().combatStatistics().acceptedShots(shooter.sessionId()));

            for (var member : before.participants()) {
                var client = f.client(member);

                if (evidence.attackKind() != 0) {
                    assertArrayEquals(spend, read(client));
                }

                if (client != sender) {
                    assertArrayEquals(root, read(client));
                }

                assertTrue(client.channel().isActive());
                assertTrue(client.channel().outboundMessages().isEmpty());
            }

            byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                .unsignedByte(0).unsignedByte(0).toByteArray();
            f.world.request(sender, ack);
            f.world.request(sender, ack);
            assertEquals(1, f.snapshot(token).completion().orElseThrow().awaitingSessions().size());
            f.world.request(f.client(before.participants().get(1)), ack);

            if (f.snapshot(token).phase() == MatchPhase.COMPLETION) {
                f.world.matchTimers.now = f.snapshot(token).completion().orElseThrow().settleNotBeforeNanos();
                f.world.matchTimers.advanceSeconds(0);
                f.world.tick();
            }

            assertEquals(MatchPhase.ACTION, f.snapshot(token).phase());

            for (var member : before.participants()) {
                var client = f.client(member);
                new TurnEndCodec().decode(read(client));
                new TurnStartCodec().decode(read(client));
                assertTrue(client.channel().isActive());
                assertTrue(client.channel().outboundMessages().isEmpty());
            }

            assertTrue(logs.text().contains("match.scenario.applied"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    static Stream<WeaponExpansionCase> baselines() throws Exception {
        return WeaponExpansionFixtures.cases().filter(evidence -> evidence.name().contains("-baseline-"));
    }
}
