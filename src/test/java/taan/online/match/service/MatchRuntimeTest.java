package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmMatchLoadingCommand;
import taan.online.match.service.model.ConfirmMatchPositionCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchDeadlineExpiredCommand;
import taan.online.match.service.model.MatchDeliveryCompletedCommand;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchToken;

class MatchRuntimeTest {
    @Test
    void revalidatesAdmissionAndStartContextWhenQueuedWorkActuallyRuns() {
        try (var f = new MatchRuntimeFixture(8)) {
            f.activate();
            f.callbacks.current = false;
            var start = f.runtime.submit(new StartMatchCommand(f.token));
            f.run();

            assertFalse(start.join());
            assertEquals(RoomMatchEndReason.PREPARATION_FAILED, f.callbacks.reason);
            assertTrue(f.owner.starts.isEmpty());
            assertFalse(f.runtime.activate(f.callbacks, List.of()));
            assertFalse(f.runtime.beginPublication(f.runtime.snapshot()));
        }

        try (var f = new MatchRuntimeFixture(8)) {
            var command = f.runtime.submit(new ReportMatchEnemiesCommand(f.context()));
            f.admitted = false;
            f.run();

            assertFalse(command.join());
            assertEquals(MatchPhase.RESERVED, f.runtime.snapshot().phase());
        }
    }

    @Test
    void discardsForgedCommandsAndStaleDeadlinesWithoutTouchingState() {
        try (var f = new MatchRuntimeFixture(8)) {
            var forged = new RoomMatchToken(new RoomId(99), f.token.generation(), f.token.matchId());
            var start = f.runtime.submit(new StartMatchCommand(forged));
            var deadline = f.runtime.submit(new MatchDeadlineExpiredCommand(new RoomMatchProgressToken(f.token, -1)));
            f.run();

            assertFalse(start.join());
            assertFalse(deadline.join());
            assertEquals(0, f.runtime.snapshot().phaseRevision());
            assertFalse(
                f.runtime.admits(
                    new MatchCommandContext(
                        f.context().matchId(),
                        new RoomId(99),
                        f.token.generation(),
                        1,
                        f.room.owner,
                        f.context().admissionId()
                    )
                )
            );
        }
    }

    @Test
    void cancellationFencesAcceptedWorkAndRunsCleanupAfterTheOwnerStops() {
        try (var f = new MatchRuntimeFixture(8)) {
            var queued = f.runtime.submit(new StartMatchCommand(f.token));
            var before = f.runtime.stop();
            var first = f.runtime.finish(() -> { });
            var second = f.runtime.finish(() -> { });

            assertEquals(MatchPhase.RESERVED, before.phase());
            assertNull(f.runtime.stop());
            assertFalse(queued.join());
            assertFalse(first.toCompletableFuture().isDone());
            assertFalse(second.toCompletableFuture().isDone());
            f.run();

            assertTrue(first.toCompletableFuture().isDone());
            assertTrue(second.toCompletableFuture().isDone());
            assertFalse(f.runtime.submit(new StartMatchCommand(f.token)).join());
            assertTrue(f.timers.deadlines.isEmpty());
        }
    }

    @Test
    void closesTheRequestingOutputAndTerminatesWhenItsQueueIsFull() {
        for (int request = 0; request < 4; request++) {
            try (var f = new MatchRuntimeFixture(1)) {
                f.activate();
                f.runtime.submit(new StartMatchCommand(f.token));
                var command = switch (request) {
                    case 0 -> new ReportMatchEnemiesCommand(f.context());
                    case 1 -> new ConfirmMatchLoadingCommand(f.context(), 0, 97, 685);
                    case 2 -> new ConfirmMatchPositionCommand(f.context(), 0, 97, 685, 0);
                    default -> new StartMatchCommand(f.token);
                };
                assertFalse(f.runtime.submit(command).join());
                f.run();

                assertEquals(request == 3 ? List.of() : List.of("MATCH_COMMAND_QUEUE_FULL"), f.owner.closed);
                assertEquals(RoomMatchEndReason.PREPARATION_FAILED, f.callbacks.reason);
                assertTrue(f.owner.starts.isEmpty());
            }
        }
    }

    @Test
    void rejectsWorkCapturedBeforeItsOwnerWasClosedAndRejectsAnotherAdmission() {
        try (var f = new MatchRuntimeFixture(8)) {
            var context = f.context();
            var wrong = new MatchCommandContext(
                context.matchId(),
                context.roomId(),
                context.roomGeneration(),
                context.channelId(),
                context.sessionId(),
                UUID.randomUUID()
            );
            var forged = f.runtime.submit(new TerminateMatchCommand(wrong, RoomMatchEndReason.GIVE_UP));
            f.run();

            assertFalse(forged.join());
            f.runtime.stop();
            var terminal = f.runtime.submit(new TerminateMatchCommand(context, RoomMatchEndReason.GIVE_UP));
            var ordinary = f.runtime.submit(new ReportMatchEnemiesCommand(context));
            f.run();

            assertFalse(terminal.join());
            assertFalse(ordinary.join());
        }
    }

    @Test
    void doesNotPublishAnActivatedReservationUntilAStartCommandIsAccepted() {
        try (var f = new MatchRuntimeFixture(8)) {
            f.activate();
            var report = f.runtime.submit(new ReportMatchEnemiesCommand(f.context()));
            f.run();

            assertFalse(report.join());
            assertTrue(f.owner.starts.isEmpty());
            assertTrue(f.runtime.unpublished());
        }
    }

    @Test
    void rejectsAdmissionAfterCloseAndExpiresAHeadlessRuntimeWithoutRoomCallbacks() {
        try (var f = new MatchRuntimeFixture(8)) {
            var context = f.context();
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            f.timers.now = 31;
            var late = f.runtime.submit(new ReportMatchEnemiesCommand(context));
            f.run();

            assertFalse(late.join());
            f.runtime.stop();

            assertFalse(f.runtime.admits(context));
        }
    }

    @Test
    void skipsQueuedWritesAfterAnExternalTerminationFence() {
        try (var f = new MatchRuntimeFixture(8)) {
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            f.runtime.stop();
            var delivery = new MatchBootstrapDelivery(
                f.runtime.snapshot(),
                List.of(new MatchSubscription(f.room.owner, f.owner)),
                f.runtime,
                f.timers,
                f.runtime.progress(),
                new WarningLogLimiter(LoggerFactory.getLogger(MatchRuntime.class), 1, Duration.ofSeconds(1), () -> 0L)
            );
            delivery.dispatch(null);

            assertTrue(f.owner.starts.isEmpty());
        }
    }

    @Test
    void treatsARejectedRoomReturnAsAnIgnoredCommand() {
        try (var f = new MatchRuntimeFixture(8)) {
            f.activate();
            f.callbacks.rejectTermination = true;
            var end = f.runtime.submit(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP));
            f.run();

            assertFalse(end.join());
            assertEquals(MatchPhase.RESERVED, f.runtime.snapshot().phase());
        }
    }

    @Test
    void validatesFailedWriteCallbacksAndIgnoresEarlyTimerSubmissions() {
        try (var f = new MatchRuntimeFixture(8)) {
            f.runtime.submit(new StartMatchCommand(f.token));
            f.run();
            var early = f.runtime.submit(new MatchDeadlineExpiredCommand(f.runtime.progress()));
            var unknown = f.runtime.submit(
                new MatchDeliveryCompletedCommand(f.runtime.progress(), UUID.randomUUID(), false)
            );
            f.run();

            assertFalse(early.join());
            assertFalse(unknown.join());
            assertEquals(MatchPhase.LOADING, f.runtime.snapshot().phase());
            assertFalse(f.runtime.unpublished());
            var end = f.runtime.submit(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP));
            f.run();

            assertFalse(end.join());
        }
    }
}
