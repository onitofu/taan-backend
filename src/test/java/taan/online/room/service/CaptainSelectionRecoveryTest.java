package taan.online.room.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.lobby.view.LobbyRoomListMapper;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.CreateRoomCommand;
import taan.online.room.service.model.RoomSelectionToken;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.SetCaptainCandidateOutcome;
import taan.online.room.view.RoomSnapshotMapper;

class CaptainSelectionRecoveryTest {
    @Test
    void supportsCaptainCreationAndMapOnlyChangesAndIgnoresRequestsWithoutMembership() {
        for (int type : new int[]{2, 3}) {
            try (var scenario = new RoomScenario()) {
                var owner = scenario.player(1, 1);
                var begin = new BeginCaptainSelectionAction(scenario.rooms);
                var candidate = new SetCaptainCandidateAction(scenario.rooms);

                assertEquals(RoomStartOutcome.IGNORED, begin.execute(owner).outcome());
                assertEquals(SetCaptainCandidateOutcome.IGNORED, candidate.execute(owner, 1, true).outcome());
                var created = scenario.create.execute(
                    owner,
                    new CreateRoomCommand(5, 0, "Room", 0, type, type == 2 ? 2 : 0, 0, 8, null, 0),
                    new RecordingRoomOutput()
                );

                assertEquals(type, new RoomSnapshotMapper().map(created.room()).info().details().gameType());
                scenario.rooms.changeMap(owner, 2);
                assertEquals(type, new LobbyRoomListMapper().map(scenario.rooms.forChannel(1).getFirst()).gameType());
                assertEquals("SELECTION_CLOSED", candidate.execute(owner, 1, true).reason());
                assertFalse(candidate.execute(owner, 1, false).candidate());
                scenario.channels.release(owner, "DISCONNECT");
                assertEquals(RoomStartOutcome.IGNORED, begin.execute(owner).outcome());
                assertEquals(SetCaptainCandidateOutcome.IGNORED, candidate.execute(owner, 1, true).outcome());
            }
        }
    }

    @Test
    void rejectsTokensForOtherRoomGenerationsAndDiscardsErrorsOfOldOrRemovedCycles() throws Exception {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            new BeginCaptainSelectionAction(scenario.rooms).execute(owner);
            var token = scenario.timers.tasks.getFirst().token();
            var oldGeneration = new RoomSelectionToken(token.roomId(), UUID.randomUUID(), token.selectionId());
            var oldCycle = new RoomSelectionToken(token.roomId(), token.generation(), UUID.randomUUID());
            var field = RoomRegistry.class.getDeclaredField("starts");
            field.setAccessible(true);
            var operations = (RoomStartOperations) field.get(scenario.rooms);
            scenario.rooms.advanceCaptainSelection(oldGeneration);
            operations.failed(oldGeneration, new IllegalStateException("Private late failure"));
            scenario.rooms.advanceCaptainSelection(oldCycle);
            operations.failed(oldCycle, new IllegalStateException("Private late failure"));

            assertEquals(RoomState.CAPTAIN_SELECTION, scenario.rooms.forChannel(1).getFirst().state());
            scenario.timers.advanceSeconds(5);
            new RequestRoomStartAction(scenario.rooms).execute(owner);
            operations.failed(token, new IllegalStateException("Private late failure"));
            scenario.rooms.release(owner, "DISCONNECT");
            scenario.rooms.release(guest, "DISCONNECT");
            operations.failed(token, new IllegalStateException("Private late failure"));

            assertFalse(logs.text().contains("room.captain.failed"));
            assertFalse(logs.text().contains("Private late failure"));
        }
    }

    @Test
    void invalidatesASelectionWhenTheChannelPresenceReturnsToLobbyAndSupportsAnOwnerInALaterSlot() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var another = scenario.player(3, 1);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.enter.execute(another, 1, "", new RecordingRoomOutput());
            scenario.rooms.changeTeam(owner, RoomTeam.RED);
            scenario.rooms.expel(owner, 3);
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeTeam(owner, RoomTeam.BLUE);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            new BeginCaptainSelectionAction(scenario.rooms).execute(owner);
            scenario.lobby(guest);
            scenario.timers.advanceSeconds(5);

            assertTrue(scenario.timers.tasks.isEmpty());
            assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
        }
    }

    @Test
    void quickJoinDoesNotAdvanceSelectionsOfAnotherChannel() {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var foreign = scenario.player(3, 2);
            scenario.create.execute(owner, RoomScenario.command(), new RecordingRoomOutput());
            scenario.enter.execute(guest, 1, "", new RecordingRoomOutput());
            scenario.rooms.setReady(guest, true);
            scenario.rooms.changeSettings(owner, 1, 2, 2);
            new BeginCaptainSelectionAction(scenario.rooms).execute(owner);
            scenario.timers.now = 11_000_000_000L;
            scenario.quickJoin.execute(foreign, 65535, new RecordingRoomOutput());

            assertEquals(RoomState.CAPTAIN_SELECTION, scenario.rooms.forChannel(1).getFirst().state());
            scenario.timers.advanceSeconds(0);
            assertEquals(RoomState.WAITING, scenario.rooms.forChannel(1).getFirst().state());
        }
    }

}
