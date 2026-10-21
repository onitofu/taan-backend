package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.room.model.RoomTeam;
import taan.online.MatchResultAssertions;
import taan.online.SelectionFixtures;
import taan.online.TeamScenarioPackets;
import taan.online.match.protocol.codec.MakeGhostRequestCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PqGamePlayMakeGhost;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;

class TeamCombatConnectionTest {
    @Test
    void relaysAllFourWeaponsToEveryPeerAndKeepsDeadParticipantsConnectedAcrossPhysicalSlots() throws Exception {
        for (int[] sizes : new int[][]{{2, 2}, {1, 4}, {4, 1}, {4, 4}}) {
            for (boolean captain : new boolean[]{false, true}) {
                try (var f = new TeamConnectionFixture(sizes[0], sizes[1], captain)) {
                    var seen = new HashSet<Integer>();

                    for (int turn = 0; turn < f.clients.size(); turn++) {
                        var snapshot = f.snapshot();
                        var shooter = snapshot.participants().stream()
                            .filter(p -> p.slotIndex() == snapshot.turn().activeSlot()).findFirst().orElseThrow();
                        seen.add(shooter.weaponId());
                        int target = snapshot.participants().stream().filter(p -> p.team() != shooter.team())
                            .findFirst().orElseThrow().slotIndex();
                        var packet = TeamScenarioPackets.shot(
                            snapshot,
                            snapshot.captain(shooter.slotIndex()) ? List.of(1) : List.of(),
                            target,
                            0
                        );
                        byte[] root = new ScenarioCodec().encode(packet);
                        var active = f.sessions.get(shooter.sessionId());
                        var frame = Unpooled.buffer();
                        active.channel().pipeline().addFirst(new FrameDecoder(65536, 10));

                        try {
                            frame.writeIntLE(root.length).writeBytes(root);

                            while (frame.isReadable()) {
                                active.channel().writeInbound(
                                    frame.readRetainedSlice(Math.min(7, frame.readableBytes()))
                                );
                            }

                            active.run();
                            f.world.tick();
                        } finally {
                            frame.release();
                            active.channel().pipeline().remove(FrameDecoder.class);
                        }

                        assertTrue(active.channel().outboundMessages().isEmpty());

                        for (var client : f.clients) {
                            if (client != active) {
                                assertArrayEquals(root, read(client));
                            }
                        }

                        byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                            .unsignedByte(shooter.slotIndex()).unsignedByte(0).toByteArray();
                        f.world.request(active, ack);
                        f.world.request(active, ack);

                        for (var client : f.clients) {
                            if (client != active) {
                                f.world.request(client, ack);
                            }
                        }

                        for (var client : f.clients) {
                            new TurnEndCodec().decode(read(client));
                            new TurnStartCodec().decode(read(client));
                            assertTrue(client.channel().isActive());
                        }
                    }

                    assertEquals(Set.of(0, 3, 8, 10), seen);
                }
            }
        }
    }

    @Test
    void keepsANonCaptainAsASpectatorAndDeliversTheCaptainOutcomeToAllRemainingClients() throws Exception {
        try (var f = new TeamConnectionFixture(2, 2, true)) {
            var before = f.snapshot();
            var shooter = before.participants().stream().filter(p -> p.slotIndex() == before.turn().activeSlot())
                .findFirst().orElseThrow();
            var victim = before.participants().stream().filter(p -> p.team() != shooter.team())
                .filter(p -> !before.captain(p.slotIndex())).findFirst().orElseThrow();
            kill(f, victim.slotIndex());
            var observer = f.sessions.get(victim.sessionId());
            f.world.request(observer, new MakeGhostRequestCodec().encode(new PqGamePlayMakeGhost(victim.slotIndex())));
            f.world.request(observer, SelectionFixtures.cash(0, 0));
            assertArrayEquals(SelectionFixtures.cash(0, 0), read(observer));
            assertTrue(observer.channel().isActive());
            assertEquals(0, f.snapshot().actors().get(victim.sessionId()).hp());

            var snapshot = f.snapshot();
            var next = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
                .findFirst().orElseThrow();
            var captain = snapshot.participants().stream().filter(p -> p.team() != next.team())
                .filter(p -> snapshot.captain(p.slotIndex())).findFirst().orElseThrow();
            kill(f, captain.slotIndex());
            assertTrue(f.world.matches.current(f.token).isEmpty());

            for (var client : f.clients) {
                MatchResultAssertions.readEnd(() -> read(client), next.team() == RoomTeam.BLUE ? 1 : 2);
                assertTrue(client.channel().isActive());
            }
        }
    }

    private void kill(TeamConnectionFixture f, int target) throws Exception {
        var snapshot = f.snapshot();
        var shooter = snapshot.participants().stream().filter(p -> p.slotIndex() == snapshot.turn().activeSlot())
            .findFirst().orElseThrow();
        byte[] root = new ScenarioCodec().encode(TeamScenarioPackets.shot(
            snapshot,
            snapshot.captain(shooter.slotIndex()) ? List.of(1) : List.of(),
            target,
            10000
        ));
        var active = f.sessions.get(shooter.sessionId());
        f.world.request(active, root);

        for (var client : f.clients) {
            if (client != active) {
                assertArrayEquals(root, read(client));
            }
        }

        byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
            .unsignedByte(shooter.slotIndex()).unsignedByte(0).toByteArray();

        for (var client : f.clients) {
            f.world.request(client, ack);
        }

        if (f.world.matches.current(f.token).isPresent()) {
            for (var client : f.clients) {
                new TurnEndCodec().decode(read(client));
                new TurnStartCodec().decode(read(client));
            }
        }
    }
}
