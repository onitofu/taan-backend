package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;

class RoomSlotConnectionTest {
    @Test
    void broadcastsSafeChangesAndLeavesTheRoomUsableAfterSilentRejections() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
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
            world.request(owner, root("PqGameSession_OpenCloseSlot-close"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), read(owner));
            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), read(guest));
            assertNull(observer.channel().readOutbound());
            world.request(owner, root("PqGameSession_OpenCloseSlot-close"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), read(owner));
            assertNull(guest.channel().readOutbound());
            world.request(guest, root("PqGameSession_OpenCloseSlot-open"));
            world.request(owner, integer(root("PqGameSession_OpenCloseSlot-open"), 5, 0, 1));
            world.request(owner, integer(root("PqGameSession_OpenCloseSlot-open"), 5, 255, 1));
            world.request(owner, integer(root("PqGameSession_OpenCloseSlot-open"), 4, 255, 1));

            assertTrue(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            assertNull(owner.channel().readOutbound());
            assertNull(guest.channel().readOutbound());
            world.request(owner, root("PqGameSession_OpenCloseSlot-open"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-open"), read(owner));
            assertArrayEquals(root("PcGameSession_OpenCloseSlot-open"), read(guest));
            world.request(owner, SelectionFixtures.cash(0, 0));

            assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
            assertTrue(logs.text().contains("room.slot.availability.rejected"));
            assertTrue(logs.events().stream().noneMatch(event -> event.getLevel().toString().equals("ERROR")));
            assertFalse(logs.text().contains("member1@example.test"));
        }
    }

    @Test
    void cancelsQueuedSlotChangesWhenTheOwnerDisconnectsAndTransfersAuthority() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            owner.channel().writeInbound(root("PqGameSession_OpenCloseSlot-close"));

            assertEquals(1, owner.jobs().size());
            owner.disconnect(false);
            world.tick();

            assertTrue(owner.jobs().isEmpty());
            assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), read(guest));
            assertArrayEquals(root("owner-transfer"), read(guest));
            world.request(guest, root("PqGameSession_OpenCloseSlot-close"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), read(guest));
            assertNull(guest.channel().readOutbound());
            assertTrue(logs.text().contains("room.request.cancelled"));
        }
    }

    @Test
    void rejectsTheControlBeforeRoomEntryAndUsesItsOwnFailureCatalog() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var client = world.client(1);
            client.channel().writeInbound(root("PqGameSession_OpenCloseSlot-close"));
            world.tick();

            assertFalse(client.channel().isActive());
            assertTrue(client.jobs().isEmpty());
            assertEquals(
                RoomLogEvent.SLOT_AVAILABILITY_FAILED,
                RoomRequestDispatcher.failureEvent(PqGameSessionOpenCloseSlot.TYPE_ID)
            );
        }
    }

    @Test
    void waitsForTheSendersWriteAndCleansUpAfterADeliveryFailure() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var writes = new QueuedOutboundWrites();
            owner.channel().pipeline().addBefore("ConnectionContext#0", "delayed", writes);
            world.request(owner, root("PqGameSession_OpenCloseSlot-close"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), read(guest));
            assertNull(owner.channel().readOutbound());
            writes.fail();
            world.tick();

            assertFalse(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            drain(guest);
            world.request(guest, root("PqGameSession_OpenCloseSlot-open"));

            assertArrayEquals(root("PcGameSession_OpenCloseSlot-open"), read(guest));
        }
    }
}
