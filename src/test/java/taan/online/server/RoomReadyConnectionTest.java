package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.ready;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionReady;

class RoomReadyConnectionTest {
    @Test
    void echoesEverySetAndOrdersTheOwnerTransferBeforeTheReadinessReset() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            var observer = world.client(3);
            drain(owner);
            drain(guest);
            int room = world.create(owner);
            drain(guest);
            drain(observer);
            world.join(guest, room);
            drain(owner);
            drain(observer);

            for (int value : new int[]{1, 1, 0, 0, 1}) {
                world.request(guest, ready(value));
                byte[] expected = RoomFixtures.root(value == 1 ? "01-Ready.reply" : "02-Ready.reply");
                assertArrayEquals(expected, read(owner));
                assertArrayEquals(expected, read(guest));
                assertNull(observer.channel().readOutbound());
            }

            world.request(guest, RoomScenario.team(1));
            world.request(guest, ready(255));

            assertNull(guest.channel().readOutbound());
            assertNull(owner.channel().readOutbound());
            assertTrue(guest.channel().isActive());
            world.request(owner, ready(1));

            assertFalse(new RoomResponseCodec().ready(read(owner)).ready());
            assertNull(guest.channel().readOutbound());
            owner.disconnect(false);
            world.tick();
            guest.channel().runPendingTasks();
            var codec = new RoomResponseCodec();

            assertEquals(0, codec.slot(read(guest)).slot().slotIndex());
            assertEquals(2, codec.option(read(guest)).option().ownerAccountId());
            assertArrayEquals(RoomFixtures.root("02-Ready.reply"), read(guest));
            world.request(guest, ready(0));

            assertArrayEquals(RoomFixtures.root("02-Ready.reply"), read(guest));
            assertTrue(guest.channel().isActive());
        }
    }

    @Test
    void cancelsQueuedReadinessOnDisconnectAndSelectsTheFailureBoundary() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            guest.channel().writeInbound(ready(1));

            assertEquals(1, guest.jobs().size());
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertTrue(logs.text().contains("room.request.cancelled"));
            assertEquals(RoomLogEvent.READY_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionReady.TYPE_ID));
        }
    }

    @Test
    void rejectsReadinessOutsideTheRoom() {
        try (var world = new RoomConnectionWorld()) {
            var client = world.client(1);
            client.channel().writeInbound(ready(1));
            world.tick();

            assertFalse(client.channel().isActive());
            assertTrue(client.jobs().isEmpty());
        }
    }

    @Test
    void awaitsTheSendersDeliveryAndRemovesItOnWriteFailure() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var writes = new QueuedOutboundWrites();
            guest.channel().pipeline().addBefore("ConnectionContext#0", "delayed", writes);
            world.request(guest, ready(1));

            assertArrayEquals(RoomFixtures.root("01-Ready.reply"), read(owner));
            assertNull(guest.channel().readOutbound());
            writes.fail();
            world.tick();
            owner.channel().runPendingTasks();

            assertFalse(guest.channel().isActive());
            assertEquals(0, new RoomResponseCodec().slot(read(owner)).slot().state());
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
        }
    }
}
