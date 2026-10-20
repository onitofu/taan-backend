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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchPowerTestPackets;
import taan.online.MatchScenarioTestPackets;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.network.FrameDecoder;

class MatchPowerConnectionTest {
    @Test
    void earnsPowerAndProcessesFragmentedUseAndCoalescedSpendAndScenarioExactlyOnce() throws Exception {
        try (var world = new RoomConnectionWorld(MatchExecutionMode.CANNON_1V1, new MatchLootPolicy(Set.of(5)))) {
            var owner = world.client(1);
            var guest = world.client(2);
            world.drainOutputs();
            int room = world.create(owner);
            world.drainOutputs();
            world.join(guest, room);
            world.request(guest, RoomScenario.ready(1));
            world.request(owner, RoomFixtures.root("start-empty"));
            var roomOwner = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
            var token = world.rooms.matchContext(roomOwner).token();
            MatchFixtures.loaded(world.matches, token);
            var initial = world.matches.current(token).orElseThrow();
            int slot = initial.turn().activeSlot();
            var sender = slot == 0 ? owner : guest;
            var observer = slot == 0 ? guest : owner;
            var member = initial.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
            pass(world, sender, initial);
            var bonus = new PcGamePlayEatBonusItem(
                slot,
                new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(slot, 11, -17, -3), -1L, 5, -1),
                Optional.empty()
            );
            world.request(sender, new EatBonusItemCodec().encode(bonus));
            world.request(sender, new NotifyEatItemCodec().encode(new PcGamePlayNotifyEatItem(slot, 0, 5)));
            pass(world, observer, world.matches.current(token).orElseThrow());
            var before = world.matches.current(token).orElseThrow();
            assertEquals(slot, before.turn().activeSlot());
            assertTrue(before.loot().inventory(member.sessionId()).receipts().getFirst().confirmed());
            var actor = before.actors().get(member.sessionId());
            var normal = MatchScenarioTestPackets.shot(slot, actor.facing(), 77, false);
            byte[] scenario = new ScenarioCodec().encode(MatchPowerTestPackets.power(normal));
            byte[] use = MatchPowerTestPackets.use(slot);
            byte[] spend = MatchPowerTestPackets.spend(slot);
            world.drainOutputs();
            sender.channel().pipeline().addFirst(new FrameDecoder(16384, 10));

            try (var logs = new LogCapture()) {
                byte[] framed = frame(use);

                for (int offset = 0; offset < framed.length; offset += 3) {
                    sender.channel().writeInbound(Unpooled.wrappedBuffer(
                        framed,
                        offset,
                        Math.min(3, framed.length - offset)
                    ));
                }

                var combined = new ByteArrayOutputStream();
                combined.write(frame(use));
                combined.write(frame(spend));
                combined.write(frame(spend));
                combined.write(frame(scenario));
                combined.write(frame(scenario));
                combined.write(frame(SelectionFixtures.cash(0, 0)));
                sender.channel().writeInbound(Unpooled.wrappedBuffer(combined.toByteArray()));

                while (!sender.jobs().isEmpty()) {
                    sender.run();
                    world.tick();
                }

                var committed = world.matches.current(token).orElseThrow();
                assertEquals(before.world().revision() + 1, committed.world().revision());
                assertEquals(actor.energy(), committed.actors().get(member.sessionId()).energy());
                assertEquals(65535, committed.loot().inventory(member.sessionId()).slots().getFirst());
                assertTrue(committed.attacks().isEmpty());
                assertEquals(1, committed.loot().grantSequence());
                assertArrayEquals(use, read(sender));
                assertArrayEquals(spend, read(sender));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(sender));
                assertArrayEquals(use, read(observer));
                assertArrayEquals(spend, read(observer));
                assertArrayEquals(scenario, read(observer));
                assertTrue(sender.channel().outboundMessages().isEmpty());
                assertTrue(observer.channel().outboundMessages().isEmpty());
                assertEquals(1, logs.text().split("event=\"match.power.activated\"", -1).length - 1);
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));

                for (var participant : committed.participants()) {
                    world.matches.confirmScenario(new ConfirmScenarioCommand(
                        MatchCommandContext.from(committed, participant.sessionId()),
                        committed.turn().revision(),
                        committed.world().revision(),
                        Optional.of(committed.completion().orElseThrow().fingerprint()),
                        slot
                    )).toCompletableFuture().join();
                }

                world.tick();
                read(sender);
                read(observer);
                assertEquals(0, new TurnStartCodec().decode(read(sender)).boxAttemptBudget());
                assertEquals(0, new TurnStartCodec().decode(read(observer)).boxAttemptBudget());
                assertTrue(sender.channel().isActive());
                assertTrue(observer.channel().isActive());
            }
        }
    }

    private void pass(RoomConnectionWorld world, ConnectionContextTestHarness client, MatchSnapshot snapshot) {
        int slot = snapshot.turn().activeSlot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        var actor = snapshot.actors().get(member.sessionId());
        var request = new PcGamePlayPassTurn(slot, actor.x(), actor.y(), actor.facing(), 0);
        world.request(client, new PassTurnCodec().encode(request));
    }

    private byte[] frame(byte[] root) {
        return ByteBuffer.allocate(root.length + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(root.length).put(root).array();
    }
}
