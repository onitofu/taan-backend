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
import taan.online.MatchFixtures;
import taan.online.TeamScenarioPackets;
import taan.online.match.model.MatchItemSource;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.network.FrameDecoder;
import taan.online.protocol.BinaryWriter;
import taan.online.room.model.RoomRule;

class PowerQuizConnectionTest {
    @Test
    void handlesBothItemProfilesWithTwoOpponentsAndDeduplicatesFragmentedAndCoalescedClaims() throws Exception {
        for (boolean captain : new boolean[]{false, true}) {
            try (var f = new TeamConnectionFixture(
                new RoomConnectionWorld(MatchExecutionMode.CLIENT_SCENARIO, MatchLootPolicy.nativeItems()),
                2,
                2,
                captain,
                "Private PowerQuiz room",
                0
            )) {
                var before = f.snapshot();
                assertEquals(RoomRule.POWER_QUIZ, before.rule());
                var shooter = before.participants().stream().filter(p -> p.slotIndex() == before.turn().activeSlot())
                    .findFirst().orElseThrow();
                var scene = TeamScenarioPackets.shot(
                    before,
                    before.captain(shooter.slotIndex()) ? List.of(1) : List.of(),
                    before.participants().stream().filter(p -> p.team() != shooter.team()).findFirst()
                        .orElseThrow().slotIndex(),
                    0
                );
                f.world.request(f.sessions.get(shooter.sessionId()), new ScenarioCodec().encode(scene));
                f.world.drainOutputs();
                assertEquals(2, f.snapshot().loot().quizOpportunities().size());
                assertTrue(f.snapshot().loot().offers().isEmpty());
                var replies = new ArrayList<byte[]>();

                for (var opportunity : f.snapshot().loot().quizOpportunities().values()) {
                    int slot = opportunity.slotIndex();
                    var recipient = f.sessions.get(opportunity.sessionId());
                    var codec = new EatBonusItemCodec();
                    var nativeClaim = codec.decode(MatchFixtures.root("powerquiz/item-05-third-slot"));
                    var command = nativeClaim.command();
                    byte[] claim = codec.encode(new PcGamePlayEatBonusItem(
                        slot,
                        new ScenarioEatItemBoxCommand(
                            new ScenarioCommandHeader(slot, 11, -17, -3),
                            -1L,
                            command.itemId(),
                            command.opaque24()
                        ),
                        nativeClaim.gauge()
                    ));
                    recipient.channel().pipeline().addFirst(new FrameDecoder(65536, 10));
                    var combined = Unpooled.buffer();
                    byte[] receipt = new BinaryWriter().unsignedInt(0x715e0944).unsignedByte(slot)
                        .unsignedByte(0).unsignedShort(1005).toByteArray();

                    try {
                        combined.writeIntLE(claim.length).writeBytes(claim);
                        combined.writeIntLE(claim.length).writeBytes(claim);
                        combined.writeIntLE(receipt.length).writeBytes(receipt);

                        while (combined.isReadable()) {
                            int length = Math.min(3, combined.readableBytes());
                            recipient.channel().writeInbound(combined.readRetainedSlice(length));
                        }

                        while (!recipient.jobs().isEmpty()) {
                            recipient.run();
                            f.world.tick();
                        }
                    } finally {
                        combined.release();
                        recipient.channel().pipeline().remove(FrameDecoder.class);
                    }

                    replies.add(claim);
                    var inventory = f.snapshot().loot().inventory(opportunity.sessionId());
                    assertEquals(List.of(1005, 65535, 65535), inventory.slots());
                    assertEquals(MatchItemSource.POWER_QUIZ, inventory.receipts().getFirst().source());
                    assertTrue(inventory.receipts().getFirst().confirmed());
                }

                for (var client : f.clients) {
                    for (byte[] reply : replies) {
                        assertArrayEquals(reply, read(client));
                    }

                    assertTrue(client.channel().isActive());
                    assertTrue(client.channel().outboundMessages().isEmpty());
                }

                assertEquals(2, f.snapshot().loot().grantSequence());
                assertFalse(f.snapshot().loot().quizOpportunities().isEmpty());
            }
        }
    }
}
