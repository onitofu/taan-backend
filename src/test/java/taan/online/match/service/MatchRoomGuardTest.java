package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.RoomMatchReservationOutcome.REJECTED;
import static taan.online.room.service.model.RoomMatchReservationOutcome.RESERVED;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.MatchRoomFixture;
import taan.online.RecordingRoomOutput;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.room.controller.RoomSettingsMapper;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.RoomInventoryMoveContextAccess;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.ExpelRoomParticipantOutcome;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.SetRoomReadyOutcome;
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.session.model.SessionId;

class MatchRoomGuardTest {
    @Test
    void rejectsInvalidOwnerReadinessTeamsMapAndClosedOrChangedSession() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var rooms = f.scenario.rooms;
            var guest = f.players.get(1);

            assertEquals("NO_MEMBERSHIP", rooms.reserveMatch(UUID.randomUUID()).reason());
            assertEquals("NOT_OWNER", rooms.reserveMatch(guest).reason());
            rooms.setReady(guest, false);

            assertEquals("PARTICIPANT_NOT_READY", rooms.reserveMatch(f.owner).reason());
            rooms.changeTeam(guest, RoomTeam.BLUE);
            rooms.setReady(guest, true);

            assertEquals("MISSING_TEAM", rooms.reserveMatch(f.owner).reason());
            rooms.setReady(guest, false);
            rooms.changeTeam(guest, RoomTeam.RED);
            rooms.setReady(guest, true);
            rooms.changeSettings(f.owner, RoomSettingsMapper.settings(2, 0, 2));

            assertEquals("UNSUPPORTED_MAP", rooms.reserveMatch(f.owner).reason());
            rooms.changeSettings(f.owner, RoomSettingsMapper.settings(1, 0, 2));
            f.scenario.sessions.close(new SessionId(guest));

            assertEquals("SESSION_CLOSED", rooms.reserveMatch(f.owner).reason());
            rooms.close();

            assertEquals(REJECTED, rooms.reserveMatch(f.owner).outcome());
        }
    }

    @Test
    void preservesTheCaptainSelectionUntilAValidReservationAndIgnoresItsOldTimers() {
        try (var f = new MatchRoomFixture(2, RoomMode.CAPTAIN_NO_ITEM)) {
            var rooms = f.scenario.rooms;

            assertEquals("CAPTAIN_SELECTION_REQUIRED", rooms.reserveMatch(f.owner).reason());
            rooms.beginCaptainSelection(f.owner);
            var callbacks = f.scenario.timers.tasks.stream().map(value -> value.command()).toList();

            assertEquals("CAPTAIN_SELECTION_IN_PROGRESS", rooms.reserveMatch(f.owner).reason());
            f.scenario.timers.advanceSeconds(5);
            var result = rooms.reserveMatch(f.owner);
            var frozen = f.scenario.matches.current(result.token()).orElseThrow();
            callbacks.forEach(Runnable::run);

            assertEquals(RESERVED, result.outcome());
            assertEquals(0, frozen.captains().blueLeaderSlot());
            assertEquals(2, frozen.captains().redLeaderSlot());
            assertEquals(frozen, f.scenario.matches.current(result.token()).orElseThrow());
        }
    }

    @Test
    void rejectsMutationsAndEntryWithoutTouchingFrozenStateInEitherPhase() {
        for (boolean commit : new boolean[]{false, true}) {
            try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
                var rooms = f.scenario.rooms;
                var extra = f.scenario.player(3, 1);
                var foreign = f.scenario.player(4, 2);
                var context = f.scenario.channels.membership(extra).orElseThrow();
                var target = rooms.entryTarget(context, 1).orElseThrow();
                var actor = new InventoryActor(
                    f.owner,
                    f.scenario.owner(f.owner).character().accountId(),
                    1,
                    f.scenario.owner(f.owner).character().gender()
                );
                var inventory = new RoomInventoryMoveContextAccess(rooms);
                var beforeEquipment = inventory.capture(actor);
                var token = rooms.reserveMatch(f.owner).token();

                if (commit) {
                    rooms.commitMatch(token).toCompletableFuture().join();
                }

                assertEquals("NOT_OWNER", rooms.reserveMatch(f.players.get(1)).reason());
                var frozen = f.scenario.matches.current(token).orElseThrow();
                var room = rooms.current(f.owner).orElseThrow();

                assertEquals(
                    EnterRoomOutcome.NOT_FOUND,
                    rooms.enter(context, target, f.scenario.owner(extra), "", new RecordingRoomOutput()).outcome()
                );
                assertEquals(
                    EnterRoomOutcome.NOT_FOUND,
                    f.scenario.enter.execute(foreign, 1, "", new RecordingRoomOutput()).outcome()
                );
                assertEquals(
                    "NO_MATCHING_ROOM",
                    f.scenario.quickJoin.execute(extra, 0xffff, new RecordingRoomOutput()).reason()
                );
                assertEquals(RoomStartOutcome.ALREADY_STARTED, rooms.requestStart(f.owner).outcome());
                assertEquals(ExpelRoomParticipantOutcome.REJECTED, rooms.expel(f.owner, 2).outcome());
                assertEquals(SetRoomReadyOutcome.REJECTED, rooms.setReady(f.players.get(1), false).outcome());
                assertEquals(ChangeRoomTeamOutcome.REJECTED, rooms.changeTeam(f.owner, RoomTeam.RED).outcome());
                assertEquals(ChangeRoomWeaponOutcome.REJECTED, rooms.changeWeapon(f.owner, 1, 0).outcome());
                assertEquals(
                    ChangeRoomSettingsOutcome.REJECTED,
                    rooms.changeSettings(f.owner, RoomSettingsMapper.settings(2, 0, 2)).outcome()
                );
                assertEquals(
                    SlotAvailabilityOutcome.REJECTED,
                    rooms.setSlotAvailability(f.owner, 7, RoomSlotState.CLOSED).outcome()
                );
                assertFalse(inventory.capture(actor).equipmentChangesAllowed());
                assertFalse(inventory.current(beforeEquipment));
                assertEquals(room, rooms.current(f.owner).orElseThrow());
                assertEquals(frozen, f.scenario.matches.current(token).orElseThrow());
                assertTrue(rooms.current(extra).isEmpty());
            }
        }
    }

    @Test
    void revalidatesAllSessionsOnCommitAndRestoresReadinessOnAnOrdinaryLeave() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            f.scenario.sessions.close(new SessionId(f.players.get(1)));

            assertEquals("SESSION_CLOSED", f.scenario.rooms.commitMatch(token).toCompletableFuture().join().reason());
            assertTrue(f.scenario.matches.current(token).isEmpty());
        }

        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            f.scenario.leave.execute(f.players.get(1), 1, 0);

            assertTrue(f.scenario.matches.current(token).isEmpty());
            assertEquals(f.owner, f.scenario.rooms.current(f.owner).orElseThrow().ownerSessionId());
        }
    }
}
