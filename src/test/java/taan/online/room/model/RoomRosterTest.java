package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;

class RoomRosterTest {
    @Test
    void validatesSlotsAndInitialLayoutAndKeepsSnapshotsImmutable() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var owner = scenario.owner(id);
            var slots = IntStream.range(0, 8)
                .mapToObj(index -> new RoomSlot(index, RoomSlotState.OPEN, null)).toList();

            assertThrows(IllegalArgumentException.class, () -> new RoomSlot(-1, RoomSlotState.OPEN, null));
            assertThrows(IllegalArgumentException.class, () -> new RoomSlot(8, RoomSlotState.OPEN, null));
            assertThrows(IllegalArgumentException.class, () -> new RoomSlot(0, RoomSlotState.OPEN, owner));
            assertThrows(IllegalArgumentException.class, () -> new RoomSlot(0, RoomSlotState.OCCUPIED, null));
            assertThrows(
                IllegalArgumentException.class,
                () -> new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), owner, List.of())
            );
            var reordered = new ArrayList<>(slots);
            reordered.set(1, slots.get(2));
            assertThrows(
                IllegalArgumentException.class,
                () -> new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), owner, reordered)
            );
            var occupied = new ArrayList<>(slots);
            occupied.set(1, new RoomSlot(1, RoomSlotState.OCCUPIED, owner));
            assertThrows(
                IllegalArgumentException.class,
                () -> new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), owner, occupied)
            );
            var room = new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), owner);

            assertEquals(id, room.ownerSessionId());
            assertThrows(UnsupportedOperationException.class, () -> room.slots().clear());
            assertTrue(room.leave(UUID.randomUUID()).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> room.enter(owner));
            assertTrue(room.leave(id).isPresent());
            assertEquals(RoomState.CLOSED, room.state());
            assertTrue(room.enter(owner).isEmpty());
        }
    }

    @Test
    void balancesAllEightSeatsAndFallsBackWhenThePreferredSideIsClosed() {
        try (var scenario = new RoomScenario()) {
            var id = scenario.player(1, 1);
            var room = new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), scenario.owner(id));
            var old = room.slots();
            int[] order = {2, 1, 3, 4, 6, 5, 7};

            for (int i = 0; i < order.length; i++) {
                var next = scenario.player(i + 2, 1);
                assertEquals(order[i], room.enter(scenario.owner(next)).orElseThrow().index());
            }

            assertEquals(8, room.playerCount());
            assertEquals(1, old.stream().filter(slot -> slot.participant() != null).count());
            assertTrue(room.enter(scenario.owner(scenario.player(9, 1))).isEmpty());
            var closed = IntStream.range(0, 8).mapToObj(
                index ->
                    new RoomSlot(index, index == 1 ? RoomSlotState.OPEN : RoomSlotState.CLOSED, null)
            ).toList();
            var limited = new Room(
                new RoomId(2),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                scenario.owner(id),
                closed
            );
            var guest = scenario.owner(scenario.player(10, 1));

            assertEquals(1, limited.enter(guest).orElseThrow().index());
            assertTrue(limited.enter(scenario.owner(scenario.player(11, 1))).isEmpty());
        }
    }
}
