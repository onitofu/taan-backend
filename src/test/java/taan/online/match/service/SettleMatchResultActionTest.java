package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.LogCapture;
import taan.online.TestLevels;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchRewardPolicies;
import taan.online.match.repository.MatchSettlement;

class SettleMatchResultActionTest {
    @Test
    void freezesTheResultAndCommitsBeforeCompletingWithoutLeakingContextOrSensitiveData() throws Exception {
        var work = new ArrayDeque<Runnable>();
        var calls = new AtomicInteger();
        Instant finished = Instant.parse("2026-10-21T00:00:00Z");
        var action = new SettleMatchResultAction(
            (result, policy) -> new MatchSettlement(result, calls.getAndIncrement() == 0),
            work::addLast,
            Clock.fixed(finished, ZoneOffset.UTC),
            new MatchRewardPolicies(TestLevels.table())
        );

        try (var fixture = new MatchOutcomeFixture(); var logs = new LogCapture()) {
            fixture.shot(77, false, false);
            var snapshot = fixture.snapshot();
            var prepared = action.prepare(snapshot, MatchOutcome.previewAborted());
            assertEquals(finished, prepared.finishedAt());
            assertEquals(77, prepared.participants().stream().mapToLong(p -> p.damage()).sum());

            MDC.put("operationId", "reward-operation");
            var pending = action.execute(prepared).toCompletableFuture();
            MDC.clear();
            assertFalse(pending.isDone());
            assertEquals(0, calls.get());
            work.removeFirst().run();

            assertEquals(prepared, pending.join());
            assertEquals(1, calls.get());
            assertTrue(logs.text().contains("match.rewards.committed"));
            assertTrue(logs.text().contains("operationId=reward-operation"));
            assertTrue(logs.text().contains("damage=\"77\""));
            assertTrue(logs.text().contains("forceMedal=\"false\""));
            assertFalse(logs.text().contains("Hero"));
            assertTrue(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty());

            var repeated = action.execute(prepared).toCompletableFuture();
            work.removeFirst().run();

            assertEquals(prepared, repeated.join());
            assertTrue(logs.text().contains("match.rewards.duplicate"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void reportsInfrastructureAndQueueFailuresOnceWithoutPublishingSuccessOrSecrets() {
        for (boolean rejected : new boolean[]{false, true}) {
            var action = new SettleMatchResultAction(
                (result, policy) -> {
                    throw new IllegalStateException("private-database-value");
                },
                work -> {
                    if (rejected) {
                        throw new RejectedExecutionException("private-queue-value");
                    }

                    work.run();
                },
                Clock.systemUTC(),
                new MatchRewardPolicies(TestLevels.table())
            );

            try (var fixture = new MatchOutcomeFixture(); var logs = new LogCapture()) {
                var prepared = action.prepare(fixture.snapshot(), MatchOutcome.previewAborted());

                assertThrows(CompletionException.class, () -> action.execute(prepared).toCompletableFuture().join());
                assertTrue(logs.text().contains("match.rewards.failed"));
                assertFalse(logs.text().contains("match.rewards.committed"));
                assertFalse(logs.text().contains("private-database-value"));
                assertFalse(logs.text().contains("private-queue-value"));
                assertFalse(logs.text().contains("Hero"));
            }
        }
    }
}
