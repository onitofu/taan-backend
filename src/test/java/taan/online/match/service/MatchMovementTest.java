package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ConfirmScenarioCommand;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.PassMatchTurnCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchMovementTest {
    @Test
    void relaysOnlyToTheObserverAndAcceptsACoalescedShotAndAcknowledgementAfterMovement() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var snapshot = f.runtime.snapshot();
            var shot = m.live.shot(false, false);
            var command = new ApplyScenarioCommand(
                shot.context(),
                shot.turnRevision(),
                shot.worldRevision(),
                shot.claims(),
                shot.root(),
                snapshot.phaseRevision()
            );
            assertTrue(m.move(m.move(1, 0, 0, MatchMovementKind.MOVE)));
            assertEquals(0, f.owner.movements.size());
            assertEquals(1, f.guest.movements.size());
            assertTrue(m.move(m.move(0, 0, 0, MatchMovementKind.SET_POS)));
            assertEquals(2, f.guest.movements.size());

            var applied = f.runtime.applyScenario(command);
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, applied.join().outcome());
            var earlyAck = new ConfirmScenarioCommand(
                f.context(),
                snapshot.turn().revision(),
                snapshot.world().revision(),
                Optional.empty(),
                0,
                snapshot.phaseRevision()
            );
            var ack = f.runtime.confirmScenario(earlyAck);
            f.runtime.confirmScenario(m.live.ack(1, false));
            f.run();
            assertTrue(ack.join());
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertEquals(2, f.runtime.snapshot().turn().activeSlot());
            assertNull(f.callbacks.reason);
        }
    }

    @Test
    void groundedPassChangesTurnOnceAndRefreshesBudgetOnlyForTheNewOwner() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var pass = m.pass();
            var staleTimer = f.timers.commands.getFirst();
            assertTrue(m.pass(pass));
            assertEquals(2, f.runtime.snapshot().turn().activeSlot());
            assertEquals(0, f.runtime.snapshot().movements().get(f.room.players.get(1)).allowance().spentMillis());
            assertEquals(MatchMovementKind.PASS_TURN, f.guest.movements.getFirst().kind());
            assertEquals(MatchMovementKind.SET_POS, f.guest.movements.getLast().kind());
            assertTrue(f.owner.stops.isEmpty());
            assertFalse(m.pass(pass));
            staleTimer.run();
            f.run();
            assertEquals(2, f.runtime.snapshot().turn().revision());
            assertNull(f.callbacks.reason);
        }
    }

    @Test
    void airbornePassWaitsForTheFixedLandingAndKeepsRepeatedReportsFromExtendingIt() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            var fall = f.runtime.snapshot().movements().get(f.room.owner).fall().orElseThrow();
            var pass = m.pass();
            assertTrue(m.pass(pass));
            assertEquals(MatchPhase.SETTLING, f.runtime.snapshot().phase());
            assertFalse(m.pass(pass));
            var deadline = f.runtime.snapshot().closure().orElseThrow().landingDeadlineNanos();
            m.timer(deadline - 1);
            assertEquals(MatchPhase.SETTLING, f.runtime.snapshot().phase());
            m.timer(deadline);
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertEquals(fall.endY(), f.runtime.snapshot().actors().get(f.room.owner).y());
            assertEquals(2, f.runtime.snapshot().turn().revision());

            var reply = f.runtime.applyScenario(m.live.shot(false, false));
            f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, reply.join().outcome());
        }
    }

    @Test
    void watchdogDrainsTheFinalAnchorAndFacingWithoutRepeatingTurnEndOrExtendingTheWindow() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            long deadline = f.runtime.snapshot().deadlineNanos();
            var stale = m.move(1, -20, 0, MatchMovementKind.MOVE);
            m.timer(deadline);
            var closure = f.runtime.snapshot().closure().orElseThrow();
            assertEquals(1, f.owner.stops.size());
            assertEquals(1, f.guest.stops.size());
            assertEquals(1, f.owner.turns.size());
            f.timers.now += 10000000;
            assertTrue(m.move(stale));
            var pass = m.pass();
            assertTrue(m.pass(new PassMatchTurnCommand(
                pass.context(),
                pass.turnRevision(),
                pass.phaseRevision(),
                pass.slot(),
                pass.x(),
                pass.y(),
                1,
                1234
            )));
            assertEquals(closure, f.runtime.snapshot().closure().orElseThrow());
            assertEquals(1, f.runtime.snapshot().actors().get(f.room.owner).facing());
            m.timer(closure.stopDrainUntilNanos());
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertEquals(1, f.owner.stops.size());
            assertEquals(2, f.owner.turns.size());
            assertFalse(m.move(stale));
        }
    }

    @Test
    void ignoresForeignWorkAndRejectsAnOwnersTeleportWithoutCommittingCoordinates() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var move = m.move(1, 0, 0, MatchMovementKind.MOVE);
            var before = f.runtime.snapshot().world();
            assertFalse(m.move(new MoveMatchActorCommand(
                move.context(),
                move.turnRevision(),
                move.phaseRevision(),
                2,
                move.x(),
                move.y(),
                0,
                move.kind()
            )));
            assertSame(before, f.runtime.snapshot().world());
            f.admitted = false;
            assertFalse(m.move(move));
            f.admitted = true;
            assertFalse(m.move(m.move(1000, 0, 0, MatchMovementKind.MOVE)));
            assertEquals(RoomMatchEndReason.INVALID_MOVEMENT, f.callbacks.reason);
            assertSame(before, f.runtime.snapshot().world());
        }
    }

    @Test
    void accumulatesBothDirectionsAndNeverRenewsTheGracePerPacket() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            assertTrue(m.move(m.move(25, 0, 0, MatchMovementKind.MOVE)));
            assertFalse(m.move(m.move(-1, 0, 0, MatchMovementKind.MOVE)));
            assertEquals(500, m.f.runtime.snapshot().movements().get(m.f.room.owner).allowance().spentMillis());
            assertEquals(RoomMatchEndReason.INVALID_MOVEMENT, m.f.callbacks.reason);
        }

        try (var m = new MovementRuntimeFixture()) {
            m.f.timers.now = 3000000000L;
            assertTrue(m.move(m.move(125, 0, 0, MatchMovementKind.MOVE)));
            assertFalse(m.move(m.move(-1, 0, 0, MatchMovementKind.MOVE)));
            assertEquals(2500, m.f.runtime.snapshot().movements().get(m.f.room.owner).allowance().spentMillis());
        }
    }

    @Test
    void aBodyBelowTheMapDiesWithoutRemovingTheRoomMember() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var actor = f.runtime.snapshot().actors().get(f.room.owner);
            int bottom = f.runtime.snapshot().definitions().map().height() + 200;
            assertTrue(m.move(m.move(0, bottom - actor.y(), 0, MatchMovementKind.MOVE)));
            assertTrue(m.pass(m.pass()));
            m.timer(f.timers.deadlines.getFirst().deadlineNanos());
            assertEquals(0, f.runtime.snapshot().actors().get(f.room.owner).hp());
            assertEquals(RoomMatchEndReason.PARTICIPANT_ELIMINATED, f.callbacks.reason);
            assertTrue(f.room.scenario.rooms.current(f.room.owner).isPresent());
        }
    }
}
