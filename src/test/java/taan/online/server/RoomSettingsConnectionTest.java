package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.map;
import static taan.online.RoomScenario.settings;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PcGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeMap;

class RoomSettingsConnectionTest {
    @Test
    void confirmsChangesAndRepeatsToMembersAndResyncsOnlyTheRejectedInitiator() throws Exception {
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
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            var codec = new RoomResponseCodec();
            var expected = new PcGameSessionChangeGameOption(2, 1, 0, 255);
            world.request(owner, RoomFixtures.root("04-ChangeGameOption.request"));

            assertEquals(expected, codec.settings(read(owner)));
            assertEquals(expected, codec.settings(read(guest)));
            drain(observer);
            world.request(owner, settings(2, 1, 0, 0));

            assertEquals(expected, codec.settings(read(owner)));
            assertEquals(expected, codec.settings(read(guest)));
            assertNull(observer.channel().readOutbound());
            world.request(guest, settings(1, 0, 2, 255));

            assertEquals(expected, codec.settings(read(guest)));
            assertEquals(
                "Requested room settings are not allowed.",
                new NoticeMessageCodec().decode(read(guest)).message()
            );
            assertNull(owner.channel().readOutbound());
            assertNull(observer.channel().readOutbound());
            assertTrue(guest.channel().isActive());
            world.request(owner, map(1));
            var current = codec.settings(read(owner));

            assertEquals(new PcGameSessionChangeGameOption(1, 1, 0, 255), current);
            assertEquals(current, codec.settings(read(guest)));
            assertNull(guest.channel().readOutbound());
            assertTrue(owner.channel().isActive());
        }
    }

    @Test
    void cancelsQueuedSettingsAndRejectsBothRequestsOutsideARoom() throws Exception {
        for (byte[] request : new byte[][]{settings(2, 1, 0, 255), map(2)}) {
            try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
                var owner = world.client(1);
                var guest = world.client(2);
                drain(owner);
                int room = world.create(owner);
                drain(guest);
                world.join(guest, room);
                drain(owner);
                guest.channel().writeInbound(request);
                guest.disconnect(false);
                world.tick();

                assertTrue(guest.jobs().isEmpty());
                assertEquals(1, world.rooms.forChannel(1).getFirst().mapId());
                assertTrue(logs.text().contains("room.request.cancelled"));
            }

            try (var world = new RoomConnectionWorld()) {
                var client = world.client(1);
                client.channel().writeInbound(request);
                world.tick();

                assertFalse(client.channel().isActive());
                assertTrue(client.jobs().isEmpty());
            }
        }

        assertEquals(RoomLogEvent.SETTINGS_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionChangeMap.TYPE_ID));
        assertEquals(
            RoomLogEvent.SETTINGS_FAILED,
            RoomRequestDispatcher.failureEvent(PqGameSessionChangeGameOption.TYPE_ID)
        );
    }

    @Test
    void keepsPublishedSettingsWhenSenderDeliveryFails() throws Exception {
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
            world.request(owner, settings(2, 1, 0, 255));
            var codec = new RoomResponseCodec();

            assertEquals(2, codec.settings(read(guest)).mapId());
            assertNull(owner.channel().readOutbound());
            assertEquals(2, world.rooms.forChannel(1).getFirst().mapId());
            writes.fail();
            world.tick();
            guest.channel().runPendingTasks();

            assertFalse(owner.channel().isActive());
            assertEquals(0, codec.slot(read(guest)).slot().state());
            assertEquals(2, codec.option(read(guest)).option().ownerAccountId());
            assertEquals(2, world.rooms.forChannel(1).getFirst().mapId());
            assertTrue(guest.channel().isActive());
        }
    }
}
