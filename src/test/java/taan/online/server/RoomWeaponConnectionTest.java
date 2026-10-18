package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.weapon;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionChangeWeapon;

class RoomWeaponConnectionTest {
    @Test
    void deliversNativeSelectionAndRepeatsOnlyToItsRoomAndRejectsForgedIdentity() throws Exception {
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

            for (int repeat = 0; repeat < 2; repeat++) {
                world.request(guest, weapon(1002, 0));

                assertArrayEquals(RoomFixtures.root("03-ChangeWeapon.reply"), read(owner));
                assertArrayEquals(RoomFixtures.root("03-ChangeWeapon.reply"), read(guest));
                assertNull(observer.channel().readOutbound());
            }

            world.request(guest, weapon(2, 11));
            world.request(guest, weapon(1001, 11));
            world.request(guest, weapon(1002, 100));

            assertArrayEquals(RoomFixtures.root("03-ChangeWeapon.reply"), read(guest));
            assertNull(guest.channel().readOutbound());
            assertNull(owner.channel().readOutbound());
            assertTrue(guest.channel().isActive());
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            world.request(guest, weapon(1002, 200));

            assertNull(guest.channel().readOutbound());
            assertNull(owner.channel().readOutbound());
            assertTrue(guest.channel().isActive());
        }
    }

    @Test
    void cancelsQueuedSelectionBeforeDisconnectAndUsesTheWeaponFailureEvent() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            guest.channel().writeInbound(weapon(1002, 0));

            assertEquals(1, guest.jobs().size());
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            assertTrue(logs.text().contains("room.request.cancelled"));
            assertEquals(
                RoomLogEvent.WEAPON_FAILED,
                RoomRequestDispatcher.failureEvent(PqGameSessionChangeWeapon.TYPE_ID)
            );
        }
    }

    @Test
    void rejectsSelectionOutsideTheRoom() {
        try (var world = new RoomConnectionWorld()) {
            var client = world.client(1);
            client.channel().writeInbound(weapon(1001, 0));
            world.tick();

            assertFalse(client.channel().isActive());
            assertTrue(client.jobs().isEmpty());
        }
    }

    @Test
    void waitsForSenderDeliveryAndCleansUpAfterFailureWithoutClosingOtherMembers() throws Exception {
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
            world.request(guest, weapon(1002, 0));

            assertArrayEquals(RoomFixtures.root("03-ChangeWeapon.reply"), read(owner));
            assertNull(guest.channel().readOutbound());
            writes.fail();
            world.tick();
            owner.channel().runPendingTasks();

            assertFalse(guest.channel().isActive());
            assertEquals(0, new RoomResponseCodec().slot(read(owner)).slot().state());
            assertTrue(owner.channel().isActive());
        }
    }
}
