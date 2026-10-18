package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.model.RoomSlotState.OCCUPIED;
import static taan.online.room.model.RoomSlotState.READY;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.room.view.RoomSnapshotMapper;

class RoomReadyTest {
    @Test
    void setsReadinessIdempotentlyAndCountsReadyOccupantsForBalancingAndCapacity() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                null,
                scenario.owner(owner)
            );
            int[] order = {2, 1, 3, 4, 6, 5, 7};

            for (int index = 0; index < order.length; index++) {
                var guest = scenario.owner(scenario.player(index + 2, 1));
                assertEquals(order[index], room.enter(guest, "").orElseThrow().index());
                assertTrue(room.setReady(guest.character().accountId(), true));
                assertFalse(room.setReady(guest.character().accountId(), true));
            }

            assertEquals(8, room.playerCount());
            assertTrue(room.enter(scenario.owner(scenario.player(9, 1)), "").isEmpty());
            var wire = new RoomSnapshotMapper().map(RoomSnapshot.from(room));
            assertEquals(8, wire.info().details().playerCount());
            assertEquals(3, wire.slots().get(2).state());
            assertEquals(2, wire.slots().get(2).occupant().accountId());
            assertThrows(
                IllegalStateException.class,
                () -> room.changeTeam(room.slots().get(2).participant().sessionId(), RoomTeam.BLUE)
            );
            assertThrows(IllegalArgumentException.class, () -> room.setSlotAvailability(3, READY));
            assertThrows(IllegalArgumentException.class, () -> new RoomSlot(0, READY, null));
            assertThrows(IllegalStateException.class, () -> room.setReady(new AccountId(1), true));
            assertThrows(IllegalArgumentException.class, () -> room.setReady(new AccountId(99), false));
            assertFalse(room.setReady(new AccountId(1), false));
            assertTrue(room.setReady(new AccountId(2), false));
            assertEquals(OCCUPIED, room.slots().get(2).state());
            assertTrue(room.setReady(new AccountId(2), true));
            room.leave(owner);

            assertEquals(7, room.playerCount());
            assertEquals(3, room.owner().character().accountId().value());
            assertEquals(OCCUPIED, room.slots().get(1).state());
            assertEquals(READY, room.slots().get(2).state());
        }
    }

    @Test
    void rejectsReadinessAfterClosingButStillCleansUpAndResetsAPromotedOwner() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                null,
                scenario.owner(owner)
            );
            room.enter(scenario.owner(guest), "");
            room.setReady(new AccountId(2), true);
            room.close();

            assertThrows(IllegalStateException.class, () -> room.setReady(new AccountId(2), false));
            room.leave(owner);

            assertEquals(guest, room.ownerSessionId());
            assertEquals(OCCUPIED, room.slots().get(2).state());
            room.leave(guest);

            assertEquals(0, room.playerCount());
        }
    }
}
