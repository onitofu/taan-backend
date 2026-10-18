package taan.online.room.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.account.model.AccountId;

class CaptainSelectionTest {
    @Test
    void freezesCandidatesOnceAndSelectsOnlyEligiblePhysicalTeamSlots() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var room = room(scenario, owner);

            for (int id = 2; id <= 4; id++) {
                room.enter(scenario.owner(scenario.player(id, 1)), "");
                room.setReady(new AccountId(id), true);
            }

            var selectedBlue = new HashSet<Integer>();
            var selectedRed = new HashSet<Integer>();

            for (int seed = 0; seed < 100; seed++) {
                var selection = new CaptainSelection(UUID.randomUUID(), 10);
                assertEquals(CaptainSelectionPhase.SELECTING, selection.phase());
                assertEquals(5_000_000_010L, selection.selectionDeadlineNanos());
                assertEquals(10_000_000_010L, selection.startDeadlineNanos());
                assertNull(selection.result());
                assertFalse(selection.selectionEnded(5_000_000_009L));
                assertTrue(selection.selectionEnded(5_000_000_010L));
                assertFalse(selection.expired(10_000_000_009L));
                assertTrue(selection.expired(10_000_000_010L));
                assertTrue(selection.setCandidate(0, true));
                assertFalse(selection.setCandidate(0, true));
                assertTrue(selection.candidate(0));
                assertTrue(selection.setCandidate(0, false));
                assertFalse(selection.setCandidate(0, false));
                var result = selection.finalizeSelection(room.slots(), new Random(seed * 71237L));

                selectedBlue.add(result.blueLeaderSlot());
                selectedRed.add(result.redLeaderSlot());
                assertEquals(CaptainSelectionPhase.FINALIZED, selection.phase());
                assertSame(result, selection.finalizeSelection(room.slots(), new Random(0)));
                assertThrows(IllegalStateException.class, () -> selection.setCandidate(0, true));
            }

            assertEquals(Set.of(0, 1), selectedBlue);
            assertEquals(Set.of(2, 3), selectedRed);
            var volunteers = new CaptainSelection(UUID.randomUUID(), 0);
            volunteers.setCandidate(1, true);
            volunteers.setCandidate(3, true);
            assertEquals(new CaptainSelectionResult(1, 3), volunteers.finalizeSelection(room.slots(), new Random(0)));
            assertThrows(
                IllegalStateException.class,
                () -> new CaptainSelection(UUID.randomUUID(), 0)
                .setCandidate(-1, true)
            );
            assertThrows(
                IllegalStateException.class,
                () -> new CaptainSelection(UUID.randomUUID(), 0)
                .setCandidate(8, true)
            );
            assertThrows(NullPointerException.class, () -> new CaptainSelection(null, 0));
        }
    }

    @Test
    void validatesLeaderSlotsAndRoomTransitionsIncludingCancellationAndClosedRooms() {
        for (int blue : new int[]{-1, 0, 1, 2, 4, 5, 8}) {
            for (int red : new int[]{-1, 0, 2, 3, 6, 7, 8}) {
                if (blue >= 0 && blue < 8 && (blue & 2) == 0 && red >= 0 && red < 8 && (red & 2) != 0) {
                    assertEquals(blue, new CaptainSelectionResult(blue, red).blueLeaderSlot());
                } else {
                    assertThrows(IllegalArgumentException.class, () -> new CaptainSelectionResult(blue, red));
                }
            }
        }

        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var room = room(scenario, owner);

            assertThrows(IllegalStateException.class, () -> room.beginCaptainSelection(UUID.randomUUID(), 0));
            assertEquals("NOT_OWNER", room.startRejection(guest));
            assertEquals("MISSING_TEAM", room.startRejection(owner));
            room.enter(scenario.owner(guest), "");
            assertEquals("PARTICIPANT_NOT_READY", room.startRejection(owner));
            room.changeSettings(RoomMode.CAPTAIN_ITEM, RoomRule.BONUS, 1, RoomScenario.maps());
            assertThrows(IllegalStateException.class, () -> room.beginCaptainSelection(UUID.randomUUID(), 0));
            room.setReady(new AccountId(2), true);
            room.beginCaptainSelection(UUID.randomUUID(), 0);

            assertEquals(RoomState.CAPTAIN_SELECTION, room.state());
            assertThrows(IllegalStateException.class, () -> room.beginCaptainSelection(UUID.randomUUID(), 0));
            room.finishCaptainSelection();
            assertEquals(RoomState.WAITING, room.state());
            assertNull(room.captainSelection());
            assertFalse(room.captainSelectionAvailable(1));
            assertThrows(IllegalStateException.class, () -> room.beginCaptainSelection(UUID.randomUUID(), 1));
            room.beginCaptainSelection(UUID.randomUUID(), 5_000_000_000L);
            room.leave(guest);
            assertNull(room.captainSelection());
            room.enter(scenario.owner(guest), "");
            room.setReady(new AccountId(2), true);
            room.beginCaptainSelection(UUID.randomUUID(), 10_000_000_000L);
            room.close();
            room.finishCaptainSelection();

            assertEquals(RoomState.CLOSED, room.state());
            assertEquals("ROOM_UNAVAILABLE", room.startRejection(owner));
            assertThrows(IllegalStateException.class, () -> room.beginCaptainSelection(UUID.randomUUID(), 0));
        }
    }

    private Room room(RoomScenario scenario, UUID owner) {
        return new Room(new RoomId(1), UUID.randomUUID(), 1, RoomScenario.settings(), null, scenario.owner(owner));
    }
}
