package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

class RoomSlotAvailabilityTest {
    @Test
    void changesOnlyEmptyWaitingSlotsAndMakesSetsIdempotent() {
        var owner = new RoomParticipant(
            UUID.randomUUID(),
            CharacterFixtures.character(1, new AccountId(1), AccountGender.MALE, "Owner"),
            3
        );
        var room = new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), null, owner);
        var previous = room.slots();

        assertTrue(room.setSlotAvailability(1, RoomSlotState.CLOSED));
        assertFalse(room.setSlotAvailability(1, RoomSlotState.CLOSED));
        assertEquals(RoomSlotState.CLOSED, room.slots().get(1).state());
        assertEquals(RoomSlotState.OPEN, previous.get(1).state());
        assertTrue(room.setSlotAvailability(1, RoomSlotState.OPEN));
        assertFalse(room.setSlotAvailability(1, RoomSlotState.OPEN));
        assertEquals(1, room.playerCount());
        assertEquals(8, room.settings().capacity());
        assertThrows(IllegalArgumentException.class, () -> room.setSlotAvailability(-1, RoomSlotState.OPEN));
        assertThrows(IllegalArgumentException.class, () -> room.setSlotAvailability(8, RoomSlotState.OPEN));
        assertThrows(IllegalArgumentException.class, () -> room.setSlotAvailability(1, null));
        assertThrows(IllegalArgumentException.class, () -> room.setSlotAvailability(1, RoomSlotState.OCCUPIED));
        assertThrows(IllegalStateException.class, () -> room.setSlotAvailability(0, RoomSlotState.OPEN));
        room.close();

        assertThrows(IllegalStateException.class, () -> room.setSlotAvailability(1, RoomSlotState.OPEN));
    }
}
