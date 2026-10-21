package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.ChannelFixtures;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.RoomScenario;
import taan.online.SelectionFixtures;
import taan.online.room.protocol.codec.RoomResponseCodec;

class RoomInventoryConnectionTest {
    @Test
    void keepsTheRoomDuringOverlaysAndMinuteKeepAlivesThenUsesOrdinaryLeaveForShopOrClientIdle() throws Exception {
        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var before = world.rooms.forChannel(1);

            for (int minute = 0; minute < 6; minute++) {
                owner.channel().advanceTimeBy(60, TimeUnit.SECONDS);
                owner.channel().runScheduledPendingTasks();
                world.request(owner, new byte[]{0x37, 0x04, (byte) 0x89, 0x18});

                assertTrue(owner.channel().isActive());
                assertNull(owner.channel().readOutbound());
                assertEquals(before, world.rooms.forChannel(1));
            }

            world.request(owner, RoomScenario.leave(room, 0));
            var codec = new RoomResponseCodec();

            assertEquals(1, codec.leave(read(owner)).success());
            assertEquals(0, codec.slot(read(guest)).slot().state());
            assertEquals(1002, codec.option(read(guest)).option().ownerCharacterId());
            assertEquals(2, world.channels.catalog().fills().get(1));
            world.request(owner, RoomScenario.leave(room, 0));
            assertEquals(1, codec.leave(read(owner)).success());
            assertNull(guest.channel().readOutbound());
            world.request(owner, SelectionFixtures.cash(0, 0));
            assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
            assertArrayEquals(SelectionFixtures.money(0), read(owner));
            world.request(owner, ChannelFixtures.enter(0));
            drain(owner);

            assertTrue(owner.channel().isActive());
            assertFalse(logs.text().contains("session.ended"));
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void preventsLateInventoryWorkFromUpdatingAnExpelledMembership() throws Exception {
        for (boolean committed : new boolean[]{false, true}) {
            try (var world = new RoomConnectionWorld()) {
                var owner = world.client(1);
                var guest = world.client(2);
                drain(owner);
                int room = world.create(owner);
                drain(guest);
                world.join(guest, room);
                drain(owner);
                var repository = guest.inventories();
                var before = repository.stored;
                Runnable kick = () -> world.request(owner, RoomScenario.expel(1002));

                if (committed) {
                    repository.onCommit = kick;
                } else {
                    repository.onApply = kick;
                }

                world.request(guest, unequip(guest));
                var codec = new RoomResponseCodec();

                assertEquals(2, codec.expel(read(guest)).targetAccountId());
                assertEquals(0, codec.slot(read(owner)).slot().state());
                assertNull(owner.channel().readOutbound());
                assertEquals(committed ? 1 : 0, repository.commits);
                assertEquals(committed ? 0 : 1, repository.rollbacks);
                assertEquals(!committed, before.equals(repository.stored));
                drain(guest);
                assertTrue(owner.channel().isActive());
                assertTrue(guest.channel().isActive());
                assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
            }
        }
    }

    @Test
    void keepsTheCommittedEquipmentWhenItsActorsRoomNotificationCannotBeDelivered() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            var writes = new QueuedOutboundWrites();
            guest.channel().pipeline().addBefore(guest.context().name(), "delayed", writes);
            world.request(guest, unequip(guest));
            var codec = new RoomResponseCodec();

            assertEquals(0, codec.slot(read(owner)).slot().occupant().appearance().parts().get(1).itemId());
            assertEquals(1, guest.inventories().commits);
            assertFalse(guest.inventories().stored.inventories().get(1).items().stream()
                .anyMatch(item -> item.slot() == 1));
            writes.fail();
            world.tick();
            world.tick();

            assertFalse(guest.channel().isActive());
            assertTrue(owner.channel().isActive());
            assertEquals(0, codec.slot(read(owner)).slot().state());
            assertEquals(1, guest.inventories().commits);
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    private byte[] unequip(ConnectionContextTestHarness client) {
        var move = InventoryFixtures.command(client.inventories()).move();
        return InventoryFixtures.move(
            2,
            move.sourceInventoryId(),
            move.instanceId(),
            move.sourceSlot(),
            move.sourceItemId(),
            move.destinationInventoryId(),
            move.destinationSlot(),
            move.destinationObservedItemId()
        );
    }
}
