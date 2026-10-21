package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.team;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomFixtures;
import taan.online.SelectionFixtures;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;

class RoomTeamConnectionTest {
    @Test
    void deliversCapturedGuestTransfersOnlyToTheRoomAndKeepsRepeatsHarmless() throws Exception {
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
            world.request(guest, team(1));

            assertArrayEquals(RoomFixtures.root("05-ChangeTeam.reply"), read(owner));
            assertArrayEquals(RoomFixtures.root("05-ChangeTeam.reply"), read(guest));
            assertNull(observer.channel().readOutbound());
            world.request(guest, team(1));
            world.request(guest, team(255));

            assertNull(guest.channel().readOutbound());
            assertNull(owner.channel().readOutbound());
            world.request(guest, team(2));

            assertArrayEquals(RoomFixtures.root("06-ChangeTeam.reply"), read(owner));
            assertArrayEquals(RoomFixtures.root("06-ChangeTeam.reply"), read(guest));
            world.request(guest, SelectionFixtures.cash(0, 0));

            assertArrayEquals(SelectionFixtures.cash(0, 0), read(guest));
            assertArrayEquals(SelectionFixtures.money(0), read(guest));
            assertTrue(guest.channel().isActive());
        }
    }

    @Test
    void cancelsAQueuedTransferBeforeDisconnectCleanupAndKeepsTheRemainingOwner() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            guest.channel().writeInbound(team(1));

            assertEquals(1, guest.jobs().size());
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            drain(owner);
            assertNull(owner.channel().readOutbound());
            assertTrue(logs.text().contains("room.request.cancelled"));
            world.request(owner, team(2));

            assertTrue(owner.channel().isActive());
            read(owner);
        }
    }

    @Test
    void rejectsTeamControlsOutsideARoomAndSelectsTheTeamFailureEvent() {
        try (var world = new RoomConnectionWorld()) {
            var client = world.client(1);
            client.channel().writeInbound(team(2));
            world.tick();

            assertFalse(client.channel().isActive());
            assertTrue(client.jobs().isEmpty());
            assertEquals(
                RoomLogEvent.TEAM_CHANGE_FAILED,
                RoomRequestDispatcher.failureEvent(PqGameSessionChangeTeam.TYPE_ID)
            );
        }
    }

    @Test
    void awaitsTheActorsDeliveryAndCleansUpItsNewPlaceWhenWritingFails() throws Exception {
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
            world.request(guest, team(1));

            assertArrayEquals(RoomFixtures.root("05-ChangeTeam.reply"), read(owner));
            assertNull(guest.channel().readOutbound());
            writes.fail();
            world.tick();

            assertFalse(guest.channel().isActive());
            assertTrue(owner.channel().isActive());
            owner.channel().runPendingTasks();
            var left = new RoomResponseCodec().slot(read(owner));
            assertEquals(1, left.slot().slotIndex());
            assertEquals(0, left.slot().state());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertNull(owner.channel().readOutbound());
        }
    }
}
