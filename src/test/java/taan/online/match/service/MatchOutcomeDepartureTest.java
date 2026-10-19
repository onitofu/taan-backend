package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.room.model.RoomState;
import taan.online.room.service.model.LeaveRoomOutcome;

class MatchOutcomeDepartureTest {
    @Test
    void preservesTheLethalCommitWhenTheWinnerSurrendersAndOnlyWaitsForTheOtherAck() throws Exception {
        try (var f = new MatchOutcomeFixture()) {
            var winner = f.active();
            var other = f.room.players.stream().filter(id -> !id.equals(winner)).findFirst().orElseThrow();
            f.shot(10000, false, false);
            var pending = f.snapshot().resolution().pendingOutcome().orElseThrow();
            var result = f.surrender(winner).toCompletableFuture();
            assertFalse(result.isDone());
            assertEquals(0, f.owner.ends);
            assertFalse(f.snapshot().completion().orElseThrow().awaitingSessions().contains(winner));
            f.ack(other);
            assertTrue(result.join().termination() != null);
            assertEquals(pending, f.owner.outcomes.getFirst());
            assertEquals(1, f.owner.ends);
            assertEquals(1, f.guest.ends);
        }
    }

    @Test
    void dropsTheDisconnectedAckButRetainsTheRosterUntilTheSurvivorReceivesEndGame() throws Exception {
        try (var f = new MatchOutcomeFixture()) {
            var winner = f.room.owner;
            var survivor = f.room.players.get(1);
            f.shot(0, false, false);
            var member = f.room.scenario.rooms.membership(winner).orElseThrow();
            f.disconnect(winner);
            assertEquals(MatchExitKind.DEPARTED, f.snapshot().resolution().exits().get(winner));
            assertEquals(0, f.guest.ends);
            assertTrue(f.room.scenario.rooms.membership(winner).isPresent());
            f.guest.onEnd = () -> assertTrue(f.room.scenario.rooms.membership(winner).isPresent());
            f.ack(survivor);
            assertEquals(1, f.guest.ends);
            assertEquals(0, f.owner.ends);
            assertEquals(MatchOutcomeCause.DEPARTURE, f.guest.outcomes.getFirst().cause());
            assertTrue(f.room.scenario.rooms.membership(winner).isEmpty());
            assertEquals(survivor, f.room.scenario.rooms.current(survivor).orElseThrow().ownerSessionId());
            f.room.scenario.rooms.release(member, "LATE_CLOSE");
            assertEquals(1, f.guest.ends);
            assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(survivor).orElseThrow().state());
        }
    }

    @Test
    void preservesTheDecidedWinnerAfterDisconnectAndDoesNotWaitWhenBothConnectionsAreGone() throws Exception {
        try (var f = new MatchOutcomeFixture()) {
            var winner = f.active();
            var survivor = f.room.players.stream().filter(id -> !id.equals(winner)).findFirst().orElseThrow();
            f.shot(10000, false, false);
            var outcome = f.snapshot().resolution().pendingOutcome().orElseThrow();
            f.disconnect(winner);
            f.ack(survivor);
            var output = survivor.equals(f.room.owner) ? f.owner : f.guest;
            assertEquals(outcome, output.outcomes.getFirst());
        }

        try (var f = new MatchOutcomeFixture()) {
            f.shot(0, false, true);
            f.disconnect(f.room.owner);
            f.disconnect(f.room.players.get(1));
            assertTrue(f.matches.current(f.token).isEmpty());
            assertTrue(f.room.scenario.rooms.forChannel(1).isEmpty());
            assertTrue(f.room.scenario.matchTimers.deadlines.isEmpty());
            assertEquals(0, f.owner.ends);
            assertEquals(0, f.guest.ends);
        }
    }

    @Test
    void timesOutTheRemainingAckAsCancellationEvenAfterTheWinnerWasDecided() throws Exception {
        try (var f = new MatchOutcomeFixture()) {
            f.shot(10000, false, false);
            var pending = f.surrender(f.room.owner).toCompletableFuture();
            f.room.scenario.matchTimers.advanceSeconds(30);
            assertTrue(pending.isDone());
            assertTrue(f.matches.current(f.token).isEmpty());
            assertTrue(f.owner.outcomes.getFirst().winningTeams().isEmpty());
            assertEquals(MatchOutcomeCause.TECHNICAL_CANCELLATION, f.owner.outcomes.getFirst().cause());
        }
    }

    @Test
    void closesOnlyTheActiveRoomLeaverAndQueuesTheSurvivorsResultBeforeRemovingTheSlot() {
        try (var f = new MatchOutcomeFixture()) {
            f.guest.onEnd = () -> assertTrue(f.room.scenario.rooms.membership(f.room.owner).isPresent());
            var result = f.room.scenario.rooms.leave(f.room.owner, f.token.roomId().value(), 0);
            assertEquals(LeaveRoomOutcome.IGNORED, result.outcome());
            assertEquals(0, f.owner.ends);
            assertEquals(1, f.guest.ends);
            assertEquals(List.of("MATCH_ROOM_LEAVE"), f.owner.closed);
            assertTrue(f.guest.closed.isEmpty());
            assertTrue(f.room.scenario.rooms.membership(f.room.owner).isEmpty());
            assertTrue(f.matches.current(f.token).isEmpty());
        }
    }
}
