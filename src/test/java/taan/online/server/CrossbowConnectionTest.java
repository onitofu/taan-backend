package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchResultAssertions;
import taan.online.CrossbowFixtures;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.TeamScenarioPackets;
import taan.online.TestFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;

class CrossbowConnectionTest {
    @Test
    void relaysUnmodifiedNativeShotsAndTheIntrinsicSpecialThroughTheProductionDispatcher() throws Exception {
        try (var f = new TeamConnectionFixture(1, 1, false, 3); var logs = new LogCapture()) {
            for (int index = 0; index < 100; index++) {
                pass(f);
            }

            while (f.snapshot().turn().activeSlot() != 0) {
                pass(f);
            }

            shot(f, "a");
            shot(f, "b");
            var member = f.snapshot().participants().stream().filter(p -> p.slotIndex() == 0).findFirst().orElseThrow();
            assertEquals(100, f.snapshot().actors().get(member.sessionId()).energy());
            var owner = f.sessions.get(member.sessionId());
            byte[] use = new UseItemCodec().encode(new PcGamePlayUseItem(0, 1, 0, 0, 255, 6));
            f.world.request(owner, use);

            for (var client : f.clients) {
                assertArrayEquals(use, read(client));
            }

            byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(0, 1, 255, List.of()));
            f.world.request(owner, spend);
            assertEquals(0, f.snapshot().actors().get(member.sessionId()).energy());
            shot(f, "special");
            var target = f.snapshot().participants().stream().filter(p -> p.slotIndex() == 2).findFirst().orElseThrow();
            assertEquals(815, f.snapshot().actors().get(target.sessionId()).hp());
            assertTrue(logs.text().contains("match.special.activated"));
            assertFalse(logs.text().contains("match.guided.activated"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    @Test
    void cancelsAnInvalidCrossbowVariantWithoutDisconnectingThePlayers() throws Exception {
        try (var f = new TeamConnectionFixture(1, 1, false, 3); var logs = new LogCapture()) {
            var before = f.snapshot();
            int slot = before.turn().activeSlot();
            var packet = TeamScenarioPackets.shot(before, List.of(), slot == 0 ? 2 : 0, 0);
            var commands = new ArrayList<>(packet.scenario().commands());
            var fire = commands.stream().filter(ScenarioFireCommand.class::isInstance)
                .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
            var w = fire.weapon();
            commands.set(commands.indexOf(fire), new ScenarioFireCommand(
                fire.header(),
                new ScenarioWeaponFields(w.sequence(), 1, w.position(), w.shotTick(), w.scalar24Bits(), w.scalar28()),
                fire.weaponId(),
                fire.attackKind(),
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            ));
            var invalid = MatchRepeatTestPackets.commands(packet, List.of(), 0, commands);
            var sender = before.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
            f.world.request(f.sessions.get(sender.sessionId()), new ScenarioCodec().encode(invalid));

            assertTrue(f.world.matches.current(f.token).isEmpty());

            for (var client : f.clients) {
                MatchResultAssertions.readEnd(() -> read(client), 0);
                assertTrue(client.channel().isActive());
            }

            assertTrue(logs.text().contains("INVALID_FIRE"));
            assertTrue(logs.text().contains("match.cancelled"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    private void shot(TeamConnectionFixture f, String name) throws Exception {
        byte[] root = CrossbowFixtures.root(name);
        var packet = new ScenarioCodec().decode(root);
        var before = f.snapshot();
        int slot = before.turn().activeSlot();
        assertEquals(packet.shooterSlot(), slot);
        var member = before.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        var sender = f.sessions.get(member.sessionId());
        var frame = Unpooled.buffer();
        sender.channel().pipeline().addFirst(new FrameDecoder(65536, 10));

        try {
            frame.writeIntLE(root.length).writeBytes(root);

            while (frame.isReadable()) {
                sender.channel().writeInbound(frame.readRetainedSlice(Math.min(7, frame.readableBytes())));
            }

            sender.run();
            f.world.tick();
        } finally {
            frame.release();
            sender.channel().pipeline().remove(FrameDecoder.class);
        }

        assertEquals(before.world().revision() + 1, f.snapshot().world().revision());
        var committed = f.snapshot().world();
        f.world.request(sender, root);
        assertEquals(committed, f.snapshot().world());

        for (var client : f.clients) {
            if (name.equals("special")) {
                var spend = new SpendItemCodec().decode(read(client));
                assertEquals(slot, spend.slotIndex());
            }

            if (client != sender) {
                assertArrayEquals(root, read(client));
            }

            assertTrue(client.channel().outboundMessages().isEmpty());
        }

        byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
            .unsignedByte(slot).unsignedByte(0).toByteArray();
        f.world.request(sender, ack);
        f.world.request(sender, ack);
        assertEquals(MatchPhase.COMPLETION, f.snapshot().phase());
        assertEquals(1, f.snapshot().completion().orElseThrow().awaitingSessions().size());

        for (var client : f.clients) {
            if (client != sender) {
                f.world.request(client, ack);
            }
        }

        if (f.snapshot().phase() == MatchPhase.COMPLETION) {
            f.world.matchTimers.now = f.snapshot().completion().orElseThrow().settleNotBeforeNanos();
            f.world.matchTimers.advanceSeconds(0);
            f.world.tick();
        }

        assertEquals(MatchPhase.ACTION, f.snapshot().phase());

        for (var client : f.clients) {
            new TurnEndCodec().decode(read(client));
            new TurnStartCodec().decode(read(client));
            assertTrue(client.channel().isActive());
            assertTrue(client.channel().outboundMessages().isEmpty());
        }
    }

    private void pass(TeamConnectionFixture f) {
        var snapshot = f.snapshot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        f.world.request(f.sessions.get(member.sessionId()), new PassTurnCodec().encode(new PcGamePlayPassTurn(
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        )));
        assertEquals(snapshot.turn().revision() + 1, f.snapshot().turn().revision());
        f.world.drainOutputs();
    }
}
