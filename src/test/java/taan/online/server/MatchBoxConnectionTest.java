package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.network.FrameDecoder;

class MatchBoxConnectionTest {
    @Test
    void commitsAFragmentedNativeScenarioRelaysOriginalBytesAndDoesNotReapplyACoalescedDuplicate() throws Exception {
        try (var world = new RoomConnectionWorld(
            MatchExecutionMode.CLIENT_SCENARIO,
            MatchLootPolicy.disabled(),
            MatchBoxPolicy.ENABLED
        )) {
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
            var before = world.matches.current(token).orElseThrow();
            int slot = before.turn().activeSlot();
            var sender = slot == 0 ? owner : guest;
            var observer = slot == 0 ? guest : owner;
            byte[] root = MatchFixtures.root(slot == 0 ? "native-cannon-0-0" : "native-cannon-2-1");
            world.drainOutputs();
            sender.channel().pipeline().addFirst(new FrameDecoder(16384, 10));

            try (var logs = new LogCapture()) {
                byte[] framed = frame(root);

                for (int offset = 0; offset < framed.length; offset += 7) {
                    sender.channel().writeInbound(Unpooled.wrappedBuffer(
                        framed,
                        offset,
                        Math.min(7, framed.length - offset)
                    ));
                }

                var combined = new ByteArrayOutputStream();
                combined.write(framed);
                combined.write(frame(SelectionFixtures.cash(0, 0)));
                sender.channel().writeInbound(Unpooled.wrappedBuffer(combined.toByteArray()));

                while (!sender.jobs().isEmpty()) {
                    sender.run();
                    world.tick();
                }

                var committed = world.matches.current(token).orElseThrow();
                assertEquals(1, committed.world().boxes().size());
                assertTrue(before.world().boxes().isEmpty());
                assertEquals(before.world().revision() + 1, committed.world().revision());
                assertArrayEquals(root, read(observer));
                assertArrayEquals(SelectionFixtures.cash(0, 0), read(sender));
                assertArrayEquals(SelectionFixtures.money(0), read(sender));
                assertTrue(sender.channel().outboundMessages().isEmpty());
                assertTrue(observer.channel().outboundMessages().isEmpty());
                assertEquals(1, logs.text().split("event=\"match.box.created\"", -1).length - 1);

                for (var member : committed.participants()) {
                    world.matches.confirmScenario(new ConfirmScenarioCommand(
                        MatchCommandContext.from(committed, member.sessionId()),
                        committed.turn().revision(),
                        committed.world().revision(),
                        Optional.of(committed.completion().orElseThrow().fingerprint()),
                        slot
                    )).toCompletableFuture().join();
                }

                world.tick();
                read(sender);
                read(observer);
                assertEquals(1, new TurnStartCodec().decode(read(sender)).boxAttemptBudget());
                assertEquals(1, new TurnStartCodec().decode(read(observer)).boxAttemptBudget());
                assertEquals(committed.world().boxes(), world.matches.current(token).orElseThrow().world().boxes());
                assertTrue(sender.channel().isActive());
                assertTrue(observer.channel().isActive());
            }
        }
    }

    private byte[] frame(byte[] root) {
        return ByteBuffer.allocate(root.length + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(root.length).put(root).array();
    }
}
