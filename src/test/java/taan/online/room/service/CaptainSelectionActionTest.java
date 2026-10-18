package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.InventoryFixtures;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.SetCaptainCandidateOutcome;
import taan.online.room.service.model.SetRoomReadyOutcome;
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.session.service.SessionInventoryAccess;

class CaptainSelectionActionTest {
    @Test
    void validatesOwnershipBothTeamsAndReadinessWhileAllowingUnequalTeams() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var extra = scenario.player(3, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            var action = new RequestRoomStartAction(scenario.rooms);

            assertEquals("MISSING_TEAM", action.execute(owner).reason());
            scenario.enter.execute(guest, 1, "", b);
            assertEquals("NOT_OWNER", action.execute(guest).reason());
            assertEquals("PARTICIPANT_NOT_READY", action.execute(owner).reason());
            scenario.rooms.setReady(guest, true);
            assertEquals("NOT_CAPTAIN_MODE", new BeginCaptainSelectionAction(scenario.rooms).execute(owner).reason());
            assertEquals(RoomStartOutcome.MATCH_UNAVAILABLE, action.execute(owner).outcome());
            assertTrue(b.notices.contains("Starting a match is currently unavailable."));
            scenario.enter.execute(extra, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(extra, true);

            assertEquals(RoomStartOutcome.MATCH_UNAVAILABLE, action.execute(owner).outcome());
            assertTrue(scenario.rooms.current(owner).isPresent());
        }
    }

    @Test
    void opensOnlyOnceEchoesCandidateSetsAndReturnsPhysicalSlotsAfterFiveSeconds() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var other = scenario.player(3, 2);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            var c = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.create.execute(other, RoomScenario.command(), c);
            scenario.enter.execute(guest, 1, "", b);
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeWeapon(owner, 1, 0);
            scenario.rooms.changeSettings(owner, 2, 3, 0);
            var before = scenario.rooms.current(owner).orElseThrow();
            var begin = new BeginCaptainSelectionAction(scenario.rooms);
            var candidate = new SetCaptainCandidateAction(scenario.rooms);
            var start = new RequestRoomStartAction(scenario.rooms);

            assertEquals("CAPTAIN_SELECTION_REQUIRED", start.execute(owner).reason());
            assertEquals(RoomStartOutcome.SELECTION_OPENED, begin.execute(owner).outcome());
            assertEquals(RoomStartOutcome.ALREADY_SELECTING, begin.execute(owner).outcome());
            assertEquals("NOT_OWNER", begin.execute(guest).reason());
            assertEquals(1, a.captainOpenings);
            assertEquals(1, b.captainOpenings);
            assertEquals(0, c.captainOpenings);
            assertEquals(2, scenario.timers.tasks.size());
            assertEquals(RoomState.CAPTAIN_SELECTION, scenario.rooms.forChannel(1).getFirst().state());
            assertEquals("CAPTAIN_SELECTION_IN_PROGRESS", start.execute(owner).reason());
            assertEquals(SetCaptainCandidateOutcome.CHANGED, candidate.execute(owner, 1, true).outcome());
            assertEquals(SetCaptainCandidateOutcome.UNCHANGED, candidate.execute(owner, 1, true).outcome());
            assertEquals(SetCaptainCandidateOutcome.CHANGED, candidate.execute(owner, 1, false).outcome());
            assertEquals(SetCaptainCandidateOutcome.UNCHANGED, candidate.execute(owner, 1, false).outcome());
            assertEquals(SetCaptainCandidateOutcome.CHANGED, candidate.execute(guest, 2, true).outcome());
            assertEquals(List.of(true, true, false, false, true), b.candidateValues);
            assertTrue(c.candidateValues.isEmpty());
            assertEquals("CHARACTER_MISMATCH", candidate.execute(owner, 2, true).reason());
            scenario.timers.advanceSeconds(5);
            assertEquals("SELECTION_CLOSED", candidate.execute(owner, 1, true).reason());
            assertFalse(a.candidateValues.getLast());
            assertTrue(candidate.execute(guest, 2, false).candidate());
            var result = start.execute(owner);

            assertEquals(RoomStartOutcome.MATCH_UNAVAILABLE, result.outcome());
            assertEquals(0, result.captains().blueLeaderSlot());
            assertEquals(2, result.captains().redLeaderSlot());
            assertEquals(before, scenario.rooms.current(owner).orElseThrow());
            assertTrue(scenario.timers.tasks.isEmpty());
            assertEquals("CAPTAIN_SELECTION_REQUIRED", start.execute(owner).reason());
            assertEquals(RoomStartOutcome.SELECTION_OPENED, begin.execute(owner).outcome());
            assertTrue(logs.text().contains("room.captain.finalized"));
            assertFalse(logs.text().contains("Hero1"));
            assertFalse(logs.text().contains("@example.test"));
        }
    }

    @Test
    void freezesRoomControlsAndEquipmentUntilTheCycleFinishes() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var entrant = scenario.player(3, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            new BeginCaptainSelectionAction(scenario.rooms).execute(owner);

            assertEquals(ChangeRoomTeamOutcome.REJECTED, scenario.rooms.changeTeam(owner, RoomTeam.RED).outcome());
            assertEquals(SetRoomReadyOutcome.REJECTED, scenario.rooms.setReady(guest, false).outcome());
            assertEquals(ChangeRoomWeaponOutcome.REJECTED, scenario.rooms.changeWeapon(owner, 1, 4).outcome());
            assertEquals(
                SlotAvailabilityOutcome.REJECTED,
                scenario.rooms.setSlotAvailability(owner, 1, RoomSlotState.CLOSED).outcome()
            );
            assertEquals(
                ChangeRoomSettingsOutcome.REJECTED,
                scenario.rooms.changeSettings(owner, 2, 1, 0).outcome()
            );
            assertEquals(
                "ROOM_UNAVAILABLE",
                scenario.enter.execute(entrant, 1, "", new RecordingRoomOutput()).reason()
            );
            var repository = new TestInventoryRepository();
            var move = new MoveInventoryItemAction(
                new SessionInventoryAccess(scenario.sessions),
                repository,
                InventoryFixtures.catalog(),
                new RoomInventoryMoveContextAccess(scenario.rooms)
            );

            assertEquals(
                InventoryMoveOutcome.STATE_RESTRICTED,
                move.execute(owner, InventoryFixtures.command(repository)).outcome()
            );
            assertEquals(0, repository.writes);
            scenario.timers.now = 5_000_000_000L;
            var finished = new RequestRoomStartAction(scenario.rooms).execute(owner);

            assertNotNull(finished.captains());
            assertEquals(ChangeRoomWeaponOutcome.UNCHANGED, scenario.rooms.changeWeapon(owner, 1, 0).outcome());
        }
    }

    @Test
    void expiresWithoutStartingAndIgnoresOldCallbacksAfterReentryAndRoomIdReuse() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            var begin = new BeginCaptainSelectionAction(scenario.rooms);
            begin.execute(owner);
            var old = List.copyOf(scenario.timers.tasks);
            scenario.timers.advanceSeconds(10);

            assertTrue(a.notices.getLast().contains("expired"));
            assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
            assertTrue(scenario.timers.tasks.isEmpty());
            begin.execute(owner);
            old.forEach(task -> task.command().run());
            assertEquals(RoomState.CAPTAIN_SELECTION, scenario.rooms.forChannel(1).getFirst().state());
            scenario.rooms.release(owner, "DISCONNECT");
            scenario.rooms.release(guest, "DISCONNECT");
            assertTrue(scenario.timers.tasks.isEmpty());
            old.forEach(task -> task.command().run());
            scenario.lobby(owner);
            scenario.lobby(guest);
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            old.forEach(task -> task.command().run());

            assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
        }
    }

    @Test
    void cancelsOnDepartureOrExpulsionAndDefersReopeningUntilTheOldModalCloses() {
        for (String mode : List.of("LEAVE_OWNER", "LEAVE_GUEST", "KICK", "CONTEXT", "SHUTDOWN")) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var guest = scenario.player(2, 1);
                var a = new RecordingRoomOutput();
                scenario.create.execute(owner, RoomScenario.command(), a);
                scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                scenario.rooms.setReady(guest, true);
                scenario.rooms.changeSettings(owner, 1, 2, 2);
                new BeginCaptainSelectionAction(scenario.rooms).execute(owner);

                switch (mode) {
                    case "LEAVE_OWNER" -> scenario.leave.execute(owner, 1, 0);
                    case "LEAVE_GUEST" -> scenario.leave.execute(guest, 1, 0);
                    case "KICK" -> scenario.rooms.expel(owner, 2);
                    case "CONTEXT" -> {
                        scenario.channels.move(guest, new MoveToCommand(0, 2, 0, 929));
                        scenario.timers.advanceSeconds(5);
                    }
                    default -> scenario.rooms.close();
                }

                assertTrue(scenario.timers.tasks.isEmpty());
                assertTrue(a.notices.stream().anyMatch(text -> text.contains("cancelled")));
            }
        }

        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            var begin = new BeginCaptainSelectionAction(scenario.rooms);
            begin.execute(owner);
            scenario.leave.execute(guest, 1, 0);
            scenario.lobby(guest);
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);

            assertEquals("PREVIOUS_WINDOW_ACTIVE", begin.execute(owner).reason());
            scenario.timers.advanceSeconds(5);
            assertEquals(RoomStartOutcome.SELECTION_OPENED, begin.execute(owner).outcome());
        }
    }

    @Test
    void rollsBackPartiallyScheduledCyclesAndHandlesUnexpectedTimerFailuresWithoutPrivateMessages() {
        for (int failAt : List.of(1, 2)) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var guest = scenario.player(2, 1);
                scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
                scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
                scenario.rooms.setReady(guest, true);
                scenario.rooms.changeSettings(owner, 1, 2, 2);
                scenario.timers.failAt = failAt;

                assertThrows(
                    IllegalStateException.class,
                    () -> new BeginCaptainSelectionAction(scenario.rooms).execute(owner)
                );
                assertTrue(scenario.timers.tasks.isEmpty());
                assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
            }
        }

        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            new BeginCaptainSelectionAction(scenario.rooms).execute(owner);
            var callback = scenario.timers.tasks.getFirst();
            scenario.timers.failClock = true;
            callback.command().run();
            callback.command().run();
            scenario.timers.failClock = false;

            assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
            assertTrue(logs.text().contains("room.captain.failed"));
            assertFalse(logs.text().contains("Private clock value"));
            assertNull(new RequestRoomStartAction(scenario.rooms).execute(owner).captains());
        }
    }
}
