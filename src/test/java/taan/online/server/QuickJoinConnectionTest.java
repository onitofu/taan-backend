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
import taan.online.ChannelFixtures;
import taan.online.LogCapture;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.room.protocol.codec.RoomResponseCodec;

class QuickJoinConnectionTest {
    @Test
    void preservesTheRoomThroughRetriesLobbyLeaveAndOwnerDisconnect() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            world.request(guest, integer(root("quickjoin-any"), 4, 7, 2));

            assertArrayEquals(root("quickjoin-fail"), read(guest));
            world.request(guest, root("quickjoin-any"));

            assertArrayEquals(root("quickjoin-fail"), read(guest));
            int room = world.create(owner);
            drain(guest);
            world.request(guest, root("quickjoin-any"));

            assertEquals(room, new RoomResponseCodec().initialize(read(guest)).info().roomId());
            assertEquals(2, new RoomResponseCodec().slot(read(owner)).slot().slotIndex());
            world.request(guest, root("quickjoin-any"));

            assertArrayEquals(root("quickjoin-fail"), read(guest));
            world.request(guest, ChannelFixtures.leave());

            assertEquals(0x22910506, ChannelFixtures.type(read(guest)));
            assertEquals(2, world.rooms.forChannel(1).getFirst().playerCount());
            owner.disconnect(false);
            world.tick();

            assertArrayEquals(root("PcGameSession_UpdateSlot-leave-blue0"), read(guest));
            assertArrayEquals(root("owner-transfer"), read(guest));
            world.request(guest, SelectionFixtures.cash(0, 0));

            assertArrayEquals(SelectionFixtures.cash(0, 0), read(guest));
            assertArrayEquals(SelectionFixtures.money(0), read(guest));
            assertTrue(guest.channel().isActive());
            assertNull(guest.channel().readOutbound());
            guest.disconnect(false);
            world.tick();

            assertTrue(world.rooms.forChannel(1).isEmpty());
        }
    }

    @Test
    void logsAnInfrastructureFailureOnceAndClosesOnlyItsConnectionWithoutLeakingMessages() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            world.create(owner);
            drain(guest);
            guest.characters().onInventories = () -> {
                throw new IllegalStateException("Private password and peer metadata");
            };
            world.request(guest, root("quickjoin-any"));

            assertFalse(guest.channel().isActive());
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertTrue(logs.text().contains("room.quick_join.failed"));
            assertEquals(
                1,
                logs.events().stream().filter(event -> event.getLevel().toString().equals("ERROR")).count()
            );
            assertFalse(logs.text().contains("Private password and peer metadata"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains("member2@example.test"));
            world.request(owner, SelectionFixtures.cash(0, 0));

            assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
            assertArrayEquals(SelectionFixtures.money(0), read(owner));
        }
    }

    @Test
    void removesQueuedQuickJoinWorkWhenItsConnectionClosesBeforeExecution() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            world.create(owner);
            drain(guest);
            int reads = guest.characters().transactions;
            guest.channel().writeInbound(root("quickjoin-any"));

            assertEquals(1, guest.jobs().size());
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertEquals(reads, guest.characters().transactions);
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertNull(owner.channel().readOutbound());
        }
    }
}
