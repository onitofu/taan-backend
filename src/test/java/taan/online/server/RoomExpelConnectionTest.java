package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.expel;
import static taan.online.RoomScenario.leave;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.LogCapture;
import taan.online.RoomEntryFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionUserExpel;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;

class RoomExpelConnectionTest {
    @Test
    void returnsTheTargetToLobbyAndLetsItRejoinAfterAcknowledgingExpulsion() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            world.request(owner, RoomFixtures.root("PqGameSession_UserExpel-native"));
            var codec = new RoomResponseCodec();

            assertEquals(new PcGameSessionUserExpel("", "", 2), codec.expel(read(guest)));
            assertNull(guest.channel().readOutbound());
            var freed = codec.slot(read(owner));
            assertEquals(2, freed.slot().slotIndex());
            assertEquals(0, freed.slot().state());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            world.request(guest, RoomEntryFixtures.enter(room));

            assertEquals(0, codec.enterFail(read(guest)).reason());
            assertTrue(guest.channel().isActive());
            world.request(guest, leave(room, 0));
            assertEquals(1, codec.leave(read(guest)).success());
            world.request(guest, leave(room, 0));
            assertEquals(1, codec.leave(read(guest)).success());
            world.request(guest, ChannelFixtures.enter(0));
            drain(guest);
            world.join(guest, room);

            assertEquals(2, codec.slot(read(owner)).slot().slotIndex());
            assertEquals(2, world.rooms.forChannel(1).getFirst().playerCount());
            assertTrue(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
        }
    }

    @Test
    void rejectsInvalidTargetsAndCancelsQueuedRequestsWithoutDisconnectingOtherPlayers() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            world.request(guest, expel(1001));
            world.request(owner, expel(1001));
            world.request(owner, expel(2));
            world.request(owner, expel(999));

            assertNull(owner.channel().readOutbound());
            assertNull(guest.channel().readOutbound());
            assertTrue(guest.channel().isActive());
            assertEquals(2, world.rooms.forChannel(1).getFirst().playerCount());
            guest.channel().writeInbound(expel(1001));
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertTrue(logs.text().contains("room.request.cancelled"));
            assertTrue(owner.channel().isActive());
        }

        try (var world = new RoomConnectionWorld()) {
            var client = world.client(1);
            client.channel().writeInbound(expel(1002));
            world.tick();

            assertFalse(client.channel().isActive());
        }

        assertEquals(RoomLogEvent.EXPEL_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionUserExpel.TYPE_ID));
    }

    @Test
    void doesNotWaitForTargetDeliveryAndKeepsOwnerConnectedWhenItFails() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var writes = new QueuedOutboundWrites();
            guest.channel().pipeline().addBefore("ConnectionContext#0", "delayed", writes);
            world.request(owner, expel(1002));
            var codec = new RoomResponseCodec();

            assertEquals(0, codec.slot(read(owner)).slot().state());
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            world.request(owner, RoomScenario.map(2));
            assertEquals(2, codec.settings(read(owner)).mapId());
            writes.fail();
            world.tick();

            assertFalse(guest.channel().isActive());
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertTrue(logs.text().contains("room.notification.failed"));
        }
    }
}
