package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.CommittedScenario;

class ScenarioConcurrencyTest {
    @Test
    void stopFencesPreparationWithoutWaitingForItsAdmissionLockAndOtherMatchesProceed() throws Exception {
        try (var first = new ScenarioRuntimeFixture(); var second = new ScenarioRuntimeFixture();
            var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var paused = new CompletableFuture<Void>();
            var resume = new CompletableFuture<Void>();
            var accesses = new AtomicInteger();
            var before = first.f.runtime.snapshot();
            first.f.onAccess = () -> {
                if (accesses.incrementAndGet() == 2) {
                    paused.complete(null);
                    resume.join();
                }
            };
            var pending = first.f.runtime.applyScenario(first.command());
            var worker = executor.submit(first.f::run);

            try {
                paused.get(5, TimeUnit.SECONDS);
                var stopped = executor.submit(first.f.runtime::stop).get(5, TimeUnit.SECONDS);
                assertSame(before, stopped);
                var other = second.f.runtime.applyScenario(second.command());
                second.f.run();
                assertEquals(ScenarioApplicationOutcome.APPLIED, other.join().outcome());
                assertTrue(other.join().committed().orElseThrow().changedCells() > 0);
            } finally {
                resume.complete(null);
            }

            worker.get(5, TimeUnit.SECONDS);
            assertEquals("STALE_COMMIT", pending.join().reason());
            assertSame(before.world(), first.f.runtime.snapshot().world());
        }
    }

    @Test
    void rechecksTheDeadlineAtCommitAndIgnoresUnrelatedDeliveryReceipts() throws Exception {
        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var before = f.runtime.snapshot();
            var checks = new AtomicInteger();
            f.onAccess = () -> {
                if (checks.incrementAndGet() == 3) {
                    f.timers.now = before.deadlineNanos();
                }
            };
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            assertEquals("STALE_COMMIT", result.join().reason());
            assertSame(before.world(), f.runtime.snapshot().world());
        }

        try (var fixture = new ScenarioRuntimeFixture()) {
            var f = fixture.f;
            var result = f.runtime.applyScenario(fixture.command());
            f.run();
            var original = result.join().committed().orElseThrow();
            var other = new CommittedScenario(
                original.scenario(),
                original.snapshot(),
                original.changedCells(),
                original.deaths()
            );
            f.runtime.scenarioDeliveryFailed(other, new IllegalStateException("stale callback"));
            f.run();
            assertTrue(f.runtime.running());
        }
    }
}
