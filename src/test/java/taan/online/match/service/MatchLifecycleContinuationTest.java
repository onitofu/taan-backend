package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.LogCapture;
import taan.online.TestLevels;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.MatchLifecycleEffect;
import taan.online.match.model.MatchLifecycleEvent;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.MatchRewardPolicies;
import taan.online.match.repository.MatchSettlement;
import taan.online.match.service.model.MatchLifecycleToken;
import taan.online.match.service.model.MatchTurnToken;
import taan.online.match.service.model.StartMatchCommand;
import taan.online.match.service.model.TerminateMatchCommand;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchToken;

class MatchLifecycleContinuationTest {
    @Test
    void rejectsStaleMatchPhaseTurnAndWorldCompletionsWithoutRunningTheirEffects() throws Exception {
        try (var live = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = live.f;
            var snapshot = f.runtime.snapshot();
            var token = f.runtime.token();
            long phase = snapshot.phaseRevision();
            long turn = snapshot.turn().revision();
            long world = snapshot.world().revision();
            var calls = new AtomicInteger();

            var other = new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID());

            for (var stale : List.of(
                new MatchLifecycleToken(other, phase, turn, world, MatchLifecycleEffect.SETTLE_RESULT),
                new MatchLifecycleToken(token, phase + 1, turn, world, MatchLifecycleEffect.SETTLE_RESULT),
                new MatchLifecycleToken(token, phase, turn + 1, world, MatchLifecycleEffect.SETTLE_RESULT),
                new MatchLifecycleToken(token, phase, turn, world + 1, MatchLifecycleEffect.SETTLE_RESULT)
            )) {
                f.runtime.lifecycle.complete(stale, calls::incrementAndGet, () -> { });
                f.run();
            }
            assertEquals(0, calls.get());
            assertEquals(snapshot, f.runtime.snapshot());
            assertTrue(logs.text().contains("match.lifecycle.event.ignored"));
            var current = new MatchLifecycleToken(token, phase, turn, world, MatchLifecycleEffect.SETTLE_RESULT);
            f.runtime.lifecycle.complete(current, calls::incrementAndGet, () -> { });
            f.run();

            assertEquals(1, calls.get());
            f.runtime.transition(MatchLifecycleEvent.SETTLE_RESULT);
            assertEquals(MatchPhase.ACTION, f.runtime.snapshot().phase());
            assertFalse(f.runtime.participation().endedAtNanos().isPresent());
            f.runtime.stop();
            assertFalse(f.runtime.lifecycle.deliveryFailed(
                new MatchTurnToken(token, turn, phase),
                new IllegalStateException("stale turn failure")
            ));
            assertFalse(f.runtime.lifecycle.expired(new MatchTurnToken(token, turn, phase), Optional.empty()));
            assertFalse(f.runtime.lifecycle.execute(new StartMatchCommand(token)));
            assertFalse(
                f.runtime.lifecycle.execute(new TerminateMatchCommand(f.context(), RoomMatchEndReason.GIVE_UP))
            );
        }
    }

    @Test
    void closesExactlyOnceWhenInitialSettlementOrDeliveryContinuationCannotBeScheduled() throws Exception {
        for (int rejectedStage = 0; rejectedStage < 3; rejectedStage++) {
            try (var live = new LiveMatchFixture()) {
                var f = live.f;
                var sql = new ArrayDeque<Runnable>();
                var pending = pending(f, sql, RoomMatchTerminationMode.RETURN_TO_ROOM);
                var returned = new AtomicInteger();
                var delivery = new CompletableFuture<Void>();
                f.owner.delivery = delivery;
                f.runtime.stop();
                f.rejectWork = rejectedStage == 0;
                var completed = f.runtime.lifecycle.finish(pending, f.room.owner, returned::incrementAndGet);
                f.run();

                if (rejectedStage > 0) {
                    assertEquals(MatchPhase.RESULT_SETTLEMENT, f.runtime.snapshot().phase());
                    f.rejectWork = rejectedStage == 1;
                    sql.removeFirst().run();
                    f.run();
                }

                if (rejectedStage == 2) {
                    assertEquals(MatchPhase.ROOM_RETURN, f.runtime.snapshot().phase());
                    assertFalse(completed.toCompletableFuture().isDone());
                    f.rejectWork = true;
                    delivery.complete(null);
                    f.run();
                }

                assertTrue(completed.toCompletableFuture().isDone());
                completed.toCompletableFuture().join();
                assertEquals(MatchPhase.CLOSED, f.runtime.snapshot().phase());
                assertEquals(1, returned.get());
                assertTrue(f.owner.closed.contains("MATCH_SETTLEMENT_FAILED"));
                assertTrue(f.guest.closed.contains("MATCH_SETTLEMENT_FAILED"));
            }
        }
    }

    @Test
    void containsAnUnexpectedReturnFailureWithoutRepeatingTheReturnCallbackOrLeakingItsMessage() throws Exception {
        try (var live = new LiveMatchFixture(); var logs = new LogCapture()) {
            var f = live.f;
            var pending = pending(f, new ArrayDeque<>(), RoomMatchTerminationMode.DISCONNECT);
            var returned = new AtomicInteger();
            f.runtime.stop();
            var completed = f.runtime.lifecycle.finish(pending, f.room.owner, () -> {
                returned.incrementAndGet();
                throw new IllegalStateException("private lifecycle continuation");
            });
            f.run();
            assertTrue(completed.toCompletableFuture().isDone());
            completed.toCompletableFuture().join();

            assertEquals(1, returned.get());
            assertEquals(MatchPhase.CLOSED, f.runtime.snapshot().phase());
            assertFalse(logs.text().contains("private lifecycle continuation"));
            assertEquals(1, logs.events().stream().filter(event -> event.getThrowableProxy() != null).count());
        }
    }

    private MatchTerminationDispatch pending(
        MatchRuntimeFixture f,
        ArrayDeque<Runnable> sql,
        RoomMatchTerminationMode mode
    ) {
        var action = new SettleMatchResultAction(
            (result, policy) -> new MatchSettlement(result, true),
            sql::addLast,
            Clock.systemUTC(),
            new MatchRewardPolicies(TestLevels.table())
        );
        var terminal = new RoomMatchTermination(f.token, RoomMatchEndReason.GIVE_UP, mode, List.of());
        var warnings = new WarningLogLimiter(LoggerFactory.getLogger(getClass()), 10, Duration.ofMinutes(1), () -> 0);
        var selections = new MatchResultSelections(Runnable::run, 8, new Random(1), warnings);
        var delivery = new MatchTerminationDelivery(
            terminal,
            List.of(
                new MatchSubscription(f.room.owner, f.owner),
                new MatchSubscription(f.room.players.get(1), f.guest)
            ),
            null,
            f.runtime.snapshot().participants(),
            selections
        );
        return new MatchTerminationDispatch(
            f.runtime,
            delivery,
            action,
            action.prepare(f.runtime.snapshot(), MatchOutcome.previewAborted()),
            mode == RoomMatchTerminationMode.RETURN_TO_ROOM
        );
    }
}
