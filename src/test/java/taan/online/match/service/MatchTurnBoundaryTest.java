package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.service.model.MatchBootstrapSettings;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.ReportMatchEnemiesCommand;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchTurnBoundaryTest {
    @Test
    void stopsBetweenTheLastAckAndPublicationWithoutIssuingAnotherTurn() throws Exception {
        try (var fixture = new LiveMatchFixture(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.run();

            try (var pause = new PausingScenarioAckAppender()) {
                f.runtime.confirmScenario(fixture.ack(1, false));
                var worker = executor.submit(f::run);

                try {
                    pause.entered.get(5, TimeUnit.SECONDS);
                    var stopped = f.runtime.stop();
                    assertEquals(0, stopped.completion().orElseThrow().awaitingSessions().size());
                    assertFalse(f.runtime.running());
                } finally {
                    pause.resume.complete(null);
                }

                worker.get(5, TimeUnit.SECONDS);
                assertEquals(1, f.owner.turns.size());
            }
        }
    }

    @Test
    void rejectsMismatchedCompletionTokensAndHandlesEarlyAndPostStopTimersWithoutMutation() throws Exception {
        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            var initial = f.runtime.snapshot();
            var progress = f.runtime.progress();
            f.runtime.turnDeadlineExpired(
                new MatchTurnToken(
                    progress.match(),
                    initial.turn().revision(),
                    initial.phaseRevision()
                ),
                Optional.empty()
            );
            f.run();
            assertTrue(f.runtime.running());
            assertFalse(f.match.acknowledgeScenario(f.room.owner));
            assertFalse(f.match.nextTurn(0, 20));

            f.runtime.applyScenario(fixture.shot(true, false));
            f.run();
            var before = f.runtime.snapshot();
            var token = new MatchTurnToken(
                f.runtime.progress().match(),
                before.turn().revision(),
                before.phaseRevision()
            );
            assertFalse(f.match.nextTurn(0, 20));
            f.runtime.turnDeadlineExpired(token, Optional.of("wrong fingerprint"));
            f.runtime.turnDeadlineExpired(token, before.completion().map(p -> p.fingerprint()));
            f.run();
            assertSame(before.world(), f.runtime.snapshot().world());
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertFalse(f.match.nextTurn(0, 20));
            f.runtime.stop();
            f.runtime.turnDeadlineExpired(token, before.completion().map(p -> p.fingerprint()));
            f.run();
            assertEquals(1, f.owner.turns.size());
        }

        try (var fixture = new ScenarioRuntimeFixture()) {
            fixture.f.runtime.applyScenario(fixture.command());
            fixture.f.run();
            assertFalse(fixture.f.match.nextTurn(0, 20));
            assertFalse(fixture.f.match.acknowledgeScenario(fixture.f.room.owner));
        }
    }

    @Test
    void handlesSchedulerRejectionAndUnexpectedAckFailuresWithBoundedCleanup() throws Exception {
        for (boolean completion : new boolean[]{true, false}) {
            try (var fixture = new LiveMatchFixture()) {
                var f = fixture.f;
                f.runtime.applyScenario(fixture.shot(false, false));
                f.run();
                var oldRejected = f.timers.rejected.getFirst();

                if (!completion) {
                    f.runtime.confirmScenario(fixture.ack(0, false));
                    f.runtime.confirmScenario(fixture.ack(1, false));
                    f.run();
                    oldRejected.run();
                    assertTrue(f.runtime.running());
                }

                f.timers.rejected.getFirst().run();
                f.run();
                assertEquals(RoomMatchEndReason.PREPARATION_FAILED, f.callbacks.reason);
            }
        }

        try (var fixture = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            var ack = fixture.ack(0, false);
            f.onAccess = () -> {
                throw new IllegalStateException("private token");
            };
            var pending = f.runtime.confirmScenario(ack);
            f.run();
            assertFalse(pending.join());
            assertEquals(RoomMatchEndReason.PREPARATION_FAILED, f.callbacks.reason);
            assertFalse(logs.text().contains("private token"));
        }
    }

    @Test
    void fencesLateDeliveryFailuresAndPreservesTheirHandlingWhenTheQueueIsFull() throws Exception {
        for (boolean overflow : new boolean[]{true, false}) {
            try (var fixture = new LiveMatchFixture()) {
                var f = fixture.f;
                f.runtime.applyScenario(fixture.shot(false, false));
                f.run();
                var delivery = new CompletableFuture<Void>();
                f.guest.turnDelivery = delivery;
                f.runtime.confirmScenario(fixture.ack(0, false));
                f.runtime.confirmScenario(fixture.ack(1, false));
                f.run();

                if (overflow) {
                    for (int index = 0; index < 8; index++) {
                        f.runtime.turnWork(() -> true);
                    }
                } else {
                    f.runtime.stop();
                }

                delivery.completeExceptionally(new IllegalStateException("transport"));
                f.run();
                assertEquals(overflow ? RoomMatchEndReason.TURN_DELIVERY_FAILED : null, f.callbacks.reason);
            }
        }

        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            var delayed = new CompletableFuture<Void>();
            f.guest.turnDelivery = delayed;
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            f.guest.turnDelivery = CompletableFuture.completedFuture(null);
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            delayed.completeExceptionally(new IllegalStateException("obsolete transport"));
            f.run();
            assertTrue(f.runtime.running());
            assertEquals(3, f.runtime.snapshot().turn().revision());
        }
    }

    @Test
    void revalidatesRecipientsBeforePublishingAndExpiresTheFollowingAction() throws Exception {
        try (var fixture = new LiveMatchFixture()) {
            var f = fixture.f;
            f.runtime.applyScenario(fixture.shot(false, false));
            f.run();
            f.runtime.confirmScenario(fixture.ack(0, false));
            f.run();
            var checks = new AtomicInteger();
            f.onAccess = () -> {
                if (checks.incrementAndGet() == 2) {
                    f.admitted = false;
                }
            };
            f.runtime.confirmScenario(fixture.ack(1, false));
            f.run();
            assertEquals(1, f.owner.turns.size());
            assertEquals(1, f.guest.turns.size());
            f.timers.now = f.runtime.snapshot().deadlineNanos();
            f.timers.commands.getFirst().run();
            f.run();
            assertEquals(RoomMatchEndReason.ACTION_TIMEOUT, f.callbacks.reason);
        }
    }

    @Test
    void lateReportsDoNotReplaceTheDedicatedActionAndCompletionTimeoutPolicies() throws Exception {
        for (boolean completion : new boolean[]{false, true}) {
            try (var fixture = new LiveMatchFixture()) {
                var f = fixture.f;

                if (completion) {
                    f.runtime.applyScenario(fixture.shot(false, false));
                    f.run();
                }

                f.timers.now = f.runtime.snapshot().deadlineNanos();
                var report = f.runtime.submit(new ReportMatchEnemiesCommand(f.context()));
                f.run();
                assertTrue(report.join());
                assertTrue(f.runtime.running());
                f.timers.commands.getFirst().run();
                f.run();
                assertEquals(
                    completion ? RoomMatchEndReason.COMPLETION_TIMEOUT : RoomMatchEndReason.ACTION_TIMEOUT,
                    f.callbacks.reason
                );
            }
        }
    }

    @Test
    void validatesBothNewTimeoutsAndKeepsNonPlayableProfilesInPreview() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                    new MatchBootstrapSettings(1, 1, 1, MatchExecutionMode.CANNON_1V1, 0, 1)
        );
        assertThrows(
            IllegalArgumentException.class,
            () ->
                    new MatchBootstrapSettings(1, 1, 1, MatchExecutionMode.CANNON_1V1, 1, 0)
        );
        var settings = new MatchBootstrapSettings(1, 1, 1, MatchExecutionMode.CANNON_1V1);
        assertSame(settings, settings.forProfile(true));
        assertEquals(MatchExecutionMode.PREVIEW, settings.forProfile(false).executionMode());
    }
}
