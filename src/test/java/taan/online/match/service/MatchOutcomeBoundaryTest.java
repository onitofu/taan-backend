package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchOutcomeBoundaryTest {
    @Test
    void rejectsForgedAndStaleInternalDeparturesAndPreservesOtherAdmissions() throws Exception {
        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var c = f.context();
            var commands = List.of(
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    UUID.randomUUID(),
                    c.channelId(),
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId() + 1,
                    c.sessionId(),
                    c.admissionId()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId(),
                    c.sessionId(),
                    UUID.randomUUID()
                ),
                new MatchCommandContext(
                    c.matchId(),
                    c.roomId(),
                    c.roomGeneration(),
                    c.channelId(),
                    UUID.randomUUID(),
                    c.admissionId()
                )
            );

            for (var context : commands) {
                assertFalse(depart(f, new DepartMatchCommand(context, RoomMatchEndReason.DISCONNECTED)));
            }
            assertFalse(depart(f, new DepartMatchCommand(c, RoomMatchEndReason.GIVE_UP)));
            assertNull(f.callbacks.reason);
            f.runtime.stop();
            assertFalse(depart(f, new DepartMatchCommand(c, RoomMatchEndReason.DISCONNECTED)));
        }

        try (var f = new MatchRuntimeFixture(8)) {
            assertFalse(depart(f, new DepartMatchCommand(f.context(), RoomMatchEndReason.DISCONNECTED)));
        }

        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CANNON_1V1)) {
            assertFalse(f.runtime.outcomes.acceptsDeparture());
            assertFalse(depart(f, new DepartMatchCommand(f.context(), RoomMatchEndReason.DISCONNECTED)));
            f.activate();
            var end = f.runtime.submit(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP));
            f.run();
            assertTrue(end.join());
            assertTrue(f.runtime.termination().termination() != null);
        }
    }

    @Test
    void rejectsAWithdrawalAfterTheCompletionDeadlineAndRechecksStopBeforeMutation() throws Exception {
        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var shot = f.runtime.applyScenario(live.shot(false, false));
            f.run();
            assertTrue(shot.join().committed().isPresent());
            f.timers.now = f.runtime.snapshot().completion().orElseThrow().deadlineNanos();
            assertFalse(depart(f, new DepartMatchCommand(f.context(), RoomMatchEndReason.ROOM_LEAVE)));
            assertEquals(RoomMatchEndReason.COMPLETION_TIMEOUT, f.callbacks.reason);
        }

        try (var live = new LiveMatchFixture()) {
            var f = live.f;
            var shot = f.runtime.applyScenario(live.shot(false, true));
            f.run();
            assertTrue(shot.join().committed().isPresent());
            f.onClock = () -> f.runtime.stop();
            assertFalse(depart(f, new DepartMatchCommand(f.context(), RoomMatchEndReason.DISCONNECTED)));
            assertTrue(f.runtime.snapshot().resolution().exits().isEmpty());
        }
    }

    @Test
    void waitsForSettlementAfterSurrenderAndRefusesFurtherActionsFromTheSurrenderedActor() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            var f = e.f;
            assertTrue(e.movement.move(e.movement.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(e.movement.pass(e.movement.pass()));
            assertEquals(MatchPhase.SETTLING, e.snapshot().phase());
            assertTrue(f.runtime.outcomes.acceptsDeparture());
            var end = f.runtime.submit(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP));
            f.run();
            assertTrue(end.join());
            assertNull(f.callbacks.reason);
            e.movement.timer(e.snapshot().closure().orElseThrow().landingDeadlineNanos());
            assertEquals(RoomMatchEndReason.GIVE_UP, f.callbacks.reason);
        }

        try (var e = new EnergyRuntimeFixture()) {
            var f = e.f;
            var shot = f.runtime.applyScenario(e.shot(false, 0));
            f.run();
            assertTrue(shot.join().committed().isPresent());
            var pending = f.runtime.submit(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP));
            f.run();
            assertTrue(pending.join());
            assertFalse(e.activate(e.use()));
            assertFalse(e.movement.move(e.movement.move(0, 0, 0, MatchMovementKind.MOVE)));
            assertFalse(f.runtime.scenarioCurrent(e.shot(false, 0)));
            f.runtime.confirmScenario(e.movement.live.ack(1, false));
            f.run();
            assertEquals(RoomMatchEndReason.GIVE_UP, f.callbacks.reason);
        }
    }

    @Test
    void doesNotWaitForASettlementThatNoConnectedPlayerCanObserve() throws Exception {
        try (var f = new MatchOutcomeFixture()) {
            f.shot(0, false, true);
            f.surrender(f.room.owner);
            f.surrender(f.room.players.get(1));
            assertEquals(0, f.owner.ends);
            f.room.scenario.matchTimers.advanceSeconds(2);
            assertEquals(1, f.owner.ends);
        }

        try (var f = new MatchOutcomeFixture()) {
            f.shot(0, false, true);
            f.ack(f.room.owner);
            f.ack(f.room.players.get(1));
            f.disconnect(f.room.owner);
            f.room.scenario.matchTimers.advanceSeconds(2);
            assertEquals(1, f.guest.ends);
        }
    }

    private boolean depart(MatchRuntimeFixture f, DepartMatchCommand command) {
        var result = f.runtime.turnWork(() -> f.runtime.outcomes.depart(command));
        f.run();
        return result.join();
    }
}
