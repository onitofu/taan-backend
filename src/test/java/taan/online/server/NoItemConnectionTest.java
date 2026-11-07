package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRepeatTestPackets;
import taan.online.TeamScenarioPackets;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomRule;

class NoItemConnectionTest {
    @Test
    void runsBothNoItemProfilesThroughProductionDispatchWithFragmentationAndCoalescedQuizClaims() throws Exception {
        for (boolean captain : new boolean[]{false, true}) {
            for (boolean quiz : new boolean[]{false, true}) {
                try (var f = new TeamConnectionFixture(
                    new RoomConnectionWorld(
                        MatchExecutionMode.CLIENT_SCENARIO,
                        MatchLootPolicy.nativeItems(),
                        MatchBoxPolicy.ENABLED
                    ),
                    2,
                    2,
                    captain,
                    quiz ? "PowerQuiz" : null,
                    false,
                    0
                )) {
                    var before = f.snapshot();
                    assertEquals(MatchPhase.ACTION, before.phase());
                    assertEquals(quiz ? RoomRule.POWER_QUIZ : RoomRule.BASIC, before.rule());
                    var shooter = before.participants().stream()
                        .filter(p -> p.slotIndex() == before.turn().activeSlot()).findFirst().orElseThrow();
                    int target = before.participants().stream().filter(p -> p.team() != shooter.team())
                        .findFirst().orElseThrow().slotIndex();
                    var selected = before.captain(shooter.slotIndex()) ? List.of(1) : List.<Integer>of();
                    var packet = TeamScenarioPackets.shot(before, selected, target, 5);
                    var events = new ArrayList<>(packet.scenario().commands());
                    events.add(new ScenarioTeamGaugeCommand(
                        new ScenarioCommandHeader(shooter.slotIndex(), 12, 1200, 0),
                        1,
                        10,
                        0
                    ));
                    packet = MatchRepeatTestPackets.commands(packet, selected, 0, events);
                    f.world.request(f.sessions.get(shooter.sessionId()), new ScenarioCodec().encode(packet));
                    f.world.drainOutputs();
                    assertEquals(MatchPhase.COMPLETION, f.snapshot().phase());
                    assertEquals(10, f.snapshot().world().teamGauges().get(shooter.team()).committed());

                    if (quiz) {
                        var codec = new EatBonusItemCodec();
                        var nativeClaim = codec.decode(MatchFixtures.root("no-item/noitem-01-correct"));
                        var opportunities = List.copyOf(f.snapshot().loot().quizOpportunities().values());

                        for (var opportunity : opportunities) {
                            int slot = opportunity.slotIndex();
                            byte[] root = codec.encode(new PcGamePlayEatBonusItem(
                                slot,
                                new ScenarioEatItemBoxCommand(
                                    new ScenarioCommandHeader(slot, 11, -17, -3),
                                    -1L,
                                    0,
                                    nativeClaim.command().opaque24()
                                ),
                                Optional.of(new ScenarioTeamGaugeCommand(
                                    new ScenarioCommandHeader(slot, 12, 0, 0),
                                    4,
                                    5,
                                    0
                                ))
                            ));
                            fragmentedTwice(f, f.sessions.get(opportunity.sessionId()), root);
                        }

                        for (var client : f.clients) {
                            for (int index = 0; index < 2; index++) {
                                var reply = codec.decode(read(client));
                                assertEquals(0, reply.command().itemId());
                                assertEquals(index * 5, reply.gauge().orElseThrow().oldGauge());
                                assertEquals((index + 1) * 5, reply.gauge().orElseThrow().newGauge());
                            }
                        }
                    }

                    assertTrue(f.snapshot().loot().inventories().isEmpty());
                    assertTrue(f.snapshot().loot().offers().isEmpty());
                    f.world.matchTimers.now = f.snapshot().completion().orElseThrow().settleNotBeforeNanos();
                    byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
                        .unsignedByte(shooter.slotIndex()).unsignedByte(0).toByteArray();

                    for (var client : f.clients) {
                        f.world.request(client, ack);
                    }

                    for (var client : f.clients) {
                        new TurnEndCodec().decode(read(client));
                        new TurnStartCodec().decode(read(client));
                        assertTrue(client.channel().isActive());
                        assertTrue(client.channel().outboundMessages().isEmpty());
                    }

                    assertEquals(MatchPhase.ACTION, f.snapshot().phase());
                    assertEquals(10, f.snapshot().world().teamGauges().get(shooter.team()).committed());
                }
            }
        }
    }

    private void fragmentedTwice(TeamConnectionFixture f, ConnectionContextTestHarness client, byte[] root) {
        client.channel().pipeline().addFirst(new FrameDecoder(65536, 10));
        var input = Unpooled.buffer();

        try {
            input.writeIntLE(root.length).writeBytes(root).writeIntLE(root.length).writeBytes(root);

            while (input.isReadable()) {
                client.channel().writeInbound(input.readRetainedSlice(Math.min(3, input.readableBytes())));
            }

            while (!client.jobs().isEmpty()) {
                client.run();
                f.world.tick();
            }
        } finally {
            input.release();
            client.channel().pipeline().remove(FrameDecoder.class);
        }
    }
}
