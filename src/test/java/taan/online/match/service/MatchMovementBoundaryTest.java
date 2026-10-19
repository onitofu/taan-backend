package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.MoveMatchActorCommand;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchMovementBoundaryTest {
    @Test
    void anUnchangedInactiveSetPosIsAcceptedButAnInactiveMoveAndWrongPhaseAreIgnored() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var snapshot = f.runtime.snapshot();
            var member = snapshot.participants().get(1);
            var actor = snapshot.actors().get(member.sessionId());
            var unchanged = new MoveMatchActorCommand(
                MatchCommandContext.from(snapshot, member.sessionId()),
                snapshot.turn().revision(),
                snapshot.phaseRevision(),
                member.slotIndex(),
                actor.x(),
                actor.y(),
                actor.facing(),
                MatchMovementKind.SET_POS
            );
            assertTrue(m.move(unchanged));
            assertFalse(m.move(new MoveMatchActorCommand(
                unchanged.context(),
                unchanged.turnRevision(),
                unchanged.phaseRevision(),
                unchanged.slot(),
                unchanged.x() + 1,
                unchanged.y(),
                unchanged.facing(),
                unchanged.kind()
            )));
            assertTrue(f.owner.movements.isEmpty());
            assertFalse(m.move(new MoveMatchActorCommand(
                unchanged.context(),
                unchanged.turnRevision(),
                unchanged.phaseRevision(),
                unchanged.slot(),
                unchanged.x(),
                unchanged.y(),
                unchanged.facing(),
                MatchMovementKind.MOVE
            )));
            var active = m.move(1, 0, 0, MatchMovementKind.MOVE);
            assertFalse(m.move(new MoveMatchActorCommand(
                active.context(),
                active.turnRevision(),
                active.phaseRevision() + 10,
                active.slot(),
                active.x(),
                active.y(),
                active.facing(),
                active.kind()
            )));
            assertNull(f.callbacks.reason);
        }
    }

    @Test
    void packetsAtTheDeadlineForceStopDrainAndLateClosingAnchorsCannotKeepTheTurnAlive() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var last = m.move(1, 0, 0, MatchMovementKind.MOVE);
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            assertTrue(m.move(last));
            assertEquals(MatchPhase.SETTLING, f.runtime.snapshot().phase());
            assertEquals(1, f.owner.stops.size());
            long drain = f.runtime.snapshot().closure().orElseThrow().stopDrainUntilNanos();
            f.timers.now = drain;
            assertFalse(m.move(m.move(0, 0, 0, MatchMovementKind.SET_POS)));
            m.timer(drain);
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertEquals(2, f.runtime.snapshot().turn().revision());
        }
    }

    @Test
    void revokedAdmissionCannotCommitOrPublishAndStopInvalidatesTheLandingTimer() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var before = f.runtime.snapshot().world();
            var checks = new AtomicInteger();
            f.onAccess = () -> {
                if (checks.incrementAndGet() == 2) {
                    f.admitted = false;
                }
            };
            assertFalse(m.move(m.move(1, 0, 0, MatchMovementKind.MOVE)));
            assertSame(before, f.runtime.snapshot().world());
            assertTrue(f.guest.movements.isEmpty());
            f.admitted = true;
            f.onAccess = () -> { };
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(m.pass(m.pass()));
            var task = f.timers.commands.getFirst();
            f.runtime.stop();
            task.run();
            f.run();
            assertEquals(1, f.owner.turns.size());
        }
    }

    @Test
    void safeDeliveryFailurePreservesCommittedCoordinatesAndEndsTheMatchOnce() throws Exception {
        try (var m = new MovementRuntimeFixture(); var logs = new LogCapture()) {
            var f = m.f;
            f.guest.delivery = new CompletableFuture<>();
            var command = m.move(1, 0, 0, MatchMovementKind.MOVE);
            assertTrue(m.move(command));
            var committed = f.runtime.snapshot().world();
            f.guest.delivery.completeExceptionally(new IllegalStateException("secret token login email"));
            f.run();
            assertEquals(RoomMatchEndReason.MOVEMENT_DELIVERY_FAILED, f.callbacks.reason);
            assertSame(committed, f.runtime.snapshot().world());
            assertFalse(logs.text().contains("secret token login email"));
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
        }
    }

    @Test
    void staleDeliveryFailureDoesNotCancelTheOpponentsNewTurn() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var delayed = new CompletableFuture<Void>();
            f.guest.delivery = delayed;
            assertTrue(m.move(m.move(0, 0, 0, MatchMovementKind.MOVE)));
            f.guest.delivery = CompletableFuture.completedFuture(null);
            assertTrue(m.pass(m.pass()));
            delayed.completeExceptionally(new IllegalStateException("obsolete transport"));
            f.run();
            assertNull(f.callbacks.reason);
            assertTrue(f.runtime.running());
        }
    }

    @Test
    void handlesStopDuringPublicationSynchronousFailuresAndRejectedTimerWork() throws Exception {
        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            f.guest.motionFailure = new IllegalStateException("safe failure");
            assertTrue(m.pass(m.pass()));
            assertEquals(RoomMatchEndReason.MOVEMENT_DELIVERY_FAILED, f.callbacks.reason);
            assertEquals(1, f.owner.turns.size());
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            f.owner.onMovement = f.runtime::stop;
            m.timer(f.runtime.snapshot().deadlineNanos());
            f.runtime.movement.timeout();
            assertFalse(f.runtime.movement.expired());
            assertEquals(1, f.owner.turns.size());
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(m.pass(m.pass()));
            f.runtime.movement.timeout();
            f.timers.rejected.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.MOVEMENT_DELIVERY_FAILED, f.callbacks.reason);
            f.timers.now += 1000000000;
            f.runtime.movement.expired();
            f.runtime.turns.advance(true);
            assertEquals(1, f.owner.turns.size());
        }
    }

    @Test
    void failedDeliveryStillCleansUpWhenTheOrdinaryQueueIsFullOrTheRuntimeHasStopped() throws Exception {
        for (boolean stopped : new boolean[]{false, true}) {
            try (var m = new MovementRuntimeFixture()) {
                var f = m.f;
                var delivery = new CompletableFuture<Void>();
                f.guest.delivery = delivery;
                assertTrue(m.move(m.move(0, 0, 0, MatchMovementKind.MOVE)));

                if (stopped) {
                    f.runtime.stop();
                } else {
                    for (int index = 0; index < 8; index++) {
                        f.runtime.turnWork(() -> true);
                    }
                }

                delivery.completeExceptionally(new IllegalStateException("transport"));
                f.run();
                assertEquals(stopped ? null : RoomMatchEndReason.MOVEMENT_DELIVERY_FAILED, f.callbacks.reason);
            }
        }
    }

    @Test
    void neverMovesAPreviewOrDeadActorAndNeverExtendsAnExplicitPassLandingDeadline() throws Exception {
        try (var f = new MatchRuntimeFixture(8)) {
            var result = f.runtime.turnWork(() -> f.runtime.movement.move(new MoveMatchActorCommand(
                f.context(),
                1,
                0,
                0,
                97,
                685,
                0,
                MatchMovementKind.MOVE
            )));
            f.run();
            assertFalse(result.join());
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            var actor = f.match.actors().get(f.room.owner);
            f.match.move(f.room.owner, actor.x(), f.match.definitions().map().height() + 200, 0, 0);
            f.match.settleMovement(3000000);
            f.runtime.refresh();
            assertFalse(m.move(m.move(0, 0, 0, MatchMovementKind.MOVE)));
            assertTrue(f.match.world().processedDeaths().contains(f.room.owner));
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            assertTrue(m.move(m.move(0, -20, 0, MatchMovementKind.MOVE)));
            assertTrue(m.pass(m.pass()));
            var world = f.match.world();
            assertFalse(f.match.nextTurn(0, 20000000000L));
            var actor = world.actors().get(f.room.owner);
            assertThrows(
                ScenarioWorldException.class,
                () -> f.match.move(
                    f.room.owner,
                    actor.x(),
                    actor.y() - 1,
                    actor.facing(),
                    1000000
                )
            );
            assertSame(world, f.match.world());
        }
    }

    @Test
    void loadingAndSettlingKeepTheirOwnDeadlinesAndQueueOverflowFencesFurtherCommands() throws Exception {
        try (var f = new MatchRuntimeFixture(8, MatchExecutionMode.CANNON_1V1)) {
            f.activate();
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.runtime.deadlineExpired(f.runtime.progress());
            f.run();
            assertEquals(RoomMatchEndReason.LOADING_TIMEOUT, f.callbacks.reason);
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;
            m.timer(f.runtime.snapshot().deadlineNanos());
            f.timers.now = f.runtime.snapshot().closure().orElseThrow().stopDrainUntilNanos();
            var report = f.runtime.submit(new ReportMatchEnemiesCommand(f.context()));
            f.run();
            assertTrue(report.join());
            f.runtime.deadlineExpired(f.runtime.progress());
            f.run();
            assertEquals(2, f.runtime.snapshot().turn().revision());
        }

        try (var m = new MovementRuntimeFixture()) {
            var f = m.f;

            for (int index = 0; index < 9; index++) {
                f.runtime.turnWork(() -> true);
            }

            f.run();
            assertEquals(RoomMatchEndReason.PREPARATION_FAILED, f.callbacks.reason);
            assertFalse(f.runtime.running());
        }
    }

}
