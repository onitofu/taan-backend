package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;

class RoomMatchModelTest {
    @Test
    void enforcesReservationAndDepartureAndClearsBindingOnRollbackOrClose() {
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
            var token = UUID.randomUUID();

            assertFalse(room.rollbackMatch(null));
            assertThrows(IllegalStateException.class, () -> room.reserveMatch(token));
            room.setReady(scenario.owner(guest).character().accountId(), true);
            room.reserveMatch(token);

            assertEquals(token, room.matchId());
            assertThrows(IllegalStateException.class, () -> room.reserveMatch(UUID.randomUUID()));
            assertThrows(IllegalStateException.class, () -> room.leave(owner));
            assertFalse(room.rollbackMatch(UUID.randomUUID()));
            assertTrue(room.rollbackMatch(token));
            assertFalse(room.rollbackMatch(token));
            assertNull(room.matchId());
            assertEquals(RoomSlotState.OCCUPIED, room.slots().get(2).state());
            room.setReady(scenario.owner(guest).character().accountId(), true);
            room.reserveMatch(token);
            room.close();

            assertNull(room.matchId());
            assertFalse(room.rollbackMatch(null));
        }
    }

    @Test
    void requiresFinalizedCaptainsAndRetainsTheirResultUntilTheOwnerCopiesIt() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var settings = new RoomSettings(new RoomTitle("Room"), RoomMode.CAPTAIN_ITEM, RoomRule.BONUS, 1, 8);
            var room = new Room(new RoomId(1), UUID.randomUUID(), 1, settings, null, scenario.owner(owner));
            room.enter(scenario.owner(guest), "");
            room.setReady(scenario.owner(guest).character().accountId(), true);

            assertThrows(IllegalStateException.class, () -> room.reserveMatch(UUID.randomUUID()));
            room.beginCaptainSelection(UUID.randomUUID(), 0);

            assertThrows(IllegalStateException.class, () -> room.reserveMatch(UUID.randomUUID()));
            room.captainSelection().finalizeSelection(room.slots(), new Random(1));
            var captains = room.captainSelection().result();
            room.reserveMatch(UUID.randomUUID());

            assertEquals(captains, room.captainSelection().result());
        }
    }

    @Test
    void canReplaceAnOldNonCannonSelectionWithTheOnlySupportedWeapon() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var old = scenario.owner(owner);
            var room = new Room(
                new RoomId(1),
                UUID.randomUUID(),
                1,
                RoomScenario.settings(),
                null,
                new RoomParticipant(owner, old.character(), 3)
            );

            assertTrue(room.changeWeapon(owner, 0));
            assertEquals(0, room.owner().weaponId());
        }
    }
}
