package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.RoomScenario;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

class RoomTeamChangeTest {
    @Test
    void movesTheSameParticipantWithoutChangingOwnerIdentityOrPersistentAppearance() {
        var owner = participant(1);
        var guest = participant(2);
        var room = new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), null, owner);
        room.enter(guest, "");
        var before = room.slots();
        var toBlue = room.changeTeam(guest.sessionId(), RoomTeam.BLUE).orElseThrow();

        assertEquals(2, toBlue.source().index());
        assertEquals(1, toBlue.destination().index());
        assertSame(guest, toBlue.destination().participant());
        assertEquals(RoomSlotState.OPEN, room.slots().get(2).state());
        assertEquals(RoomSlotState.OCCUPIED, before.get(2).state());
        assertEquals(owner.sessionId(), room.ownerSessionId());
        assertTrue(room.changeTeam(guest.sessionId(), RoomTeam.BLUE).isEmpty());
        var ownerRed = room.changeTeam(owner.sessionId(), RoomTeam.RED).orElseThrow();

        assertEquals(0, ownerRed.source().index());
        assertEquals(2, ownerRed.destination().index());
        assertSame(owner, room.owner());
        assertSame(owner.character(), room.owner().character());
        assertEquals(7, room.owner().weaponId());
        room.leave(guest.sessionId());

        assertEquals(RoomSlotState.OPEN, room.slots().get(1).state());
        assertEquals(owner.sessionId(), room.ownerSessionId());
        room.leave(owner.sessionId());

        assertEquals(RoomState.CLOSED, room.state());
    }

    @Test
    void skipsClosedAndOccupiedTargetsAndRefusesInvalidContexts() {
        var owner = participant(1);
        var room = new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), null, owner);

        for (int index : new int[]{2, 3, 6, 7}) {
            room.setSlotAvailability(index, RoomSlotState.CLOSED);
        }

        assertTrue(room.changeTeam(owner.sessionId(), RoomTeam.RED).isEmpty());
        assertEquals(1, room.playerCount());
        assertEquals(8, room.settings().capacity());
        room.setSlotAvailability(7, RoomSlotState.OPEN);

        assertEquals(7, room.changeTeam(owner.sessionId(), RoomTeam.RED).orElseThrow().destination().index());
        assertThrows(NullPointerException.class, () -> room.changeTeam(owner.sessionId(), null));
        assertThrows(IllegalArgumentException.class, () -> room.changeTeam(UUID.randomUUID(), RoomTeam.BLUE));
        room.close();

        assertThrows(IllegalStateException.class, () -> room.changeTeam(owner.sessionId(), RoomTeam.BLUE));
    }

    @Test
    void rejectsUnsafeTransferDescriptions() {
        var a = participant(1);
        var b = participant(2);
        var occupied = new RoomSlot(0, RoomSlotState.OCCUPIED, a);
        var empty = new RoomSlot(2, RoomSlotState.OPEN, null);
        var valid = new RoomSlot(2, RoomSlotState.OCCUPIED, a);

        assertThrows(IllegalArgumentException.class, () -> new RoomTeamChange(empty, valid));
        assertThrows(IllegalArgumentException.class, () -> new RoomTeamChange(occupied, empty));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomTeamChange(occupied, new RoomSlot(2, RoomSlotState.OCCUPIED, b))
        );
        assertThrows(IllegalArgumentException.class, () -> new RoomTeamChange(occupied, occupied));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RoomTeamChange(occupied, new RoomSlot(1, RoomSlotState.OCCUPIED, a))
        );
        assertEquals(a, new RoomTeamChange(occupied, valid).destination().participant());
    }

    private RoomParticipant participant(long id) {
        return new RoomParticipant(
            UUID.randomUUID(),
            CharacterFixtures.character(1000 + id, new AccountId(id), AccountGender.FEMALE, "Hero" + id),
            7
        );
    }
}
