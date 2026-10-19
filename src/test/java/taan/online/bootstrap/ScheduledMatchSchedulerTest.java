package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.match.service.model.MatchDeadline;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchToken;

class ScheduledMatchSchedulerTest {
    @Test
    void submitsDeadlinesFromTimerThreadsAndPreservesTheSchedulingContext() throws Exception {
        try (var scheduler = new ScheduledMatchScheduler(2, System::nanoTime)) {
            var done = new CountDownLatch(1);
            var error = new AtomicReference<Throwable>();
            MDC.put("operationId", "match-deadline");
            scheduler.schedule(
                new MatchDeadline(token(), System.nanoTime()),
                () -> {
                    try {
                        assertTrue(Thread.currentThread().getName().startsWith("match-timer-"));
                        assertEquals("match-deadline", MDC.get("operationId"));
                    } catch (Throwable failure) {
                        error.set(failure);
                    } finally {
                        done.countDown();
                    }
                },
                () -> error.set(new AssertionError("Unexpected rejection"))
            );
            MDC.clear();

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(null, error.get());
            assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    while (scheduler.pendingCount() != 0) {
                        Thread.sleep(1);
                    }
                }
            );
        } finally {
            MDC.clear();
        }
    }

    @Test
    void boundsDelayedWorkAndRejectsUnavailableSchedulingWithoutLeakingTasks() {
        var rejected = new AtomicInteger();

        try (var scheduler = new ScheduledMatchScheduler(1, System::nanoTime)) {
            var first = token();
            var second = token();
            long later = System.nanoTime() + TimeUnit.DAYS.toNanos(1);
            scheduler.schedule(new MatchDeadline(first, later), () -> { }, rejected::incrementAndGet);
            scheduler.schedule(new MatchDeadline(second, later), () -> { }, rejected::incrementAndGet);

            assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.schedule(new MatchDeadline(token(), later), () -> { }, rejected::incrementAndGet)
            );
            scheduler.execute(token(), () -> { }, rejected::incrementAndGet);
            assertEquals(1, rejected.get());
            scheduler.cancel(first.match().matchId());
            assertEquals(1, scheduler.pendingCount());
            scheduler.close();
            scheduler.close();

            assertEquals(0, scheduler.pendingCount());
            assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.schedule(new MatchDeadline(token(), later), () -> { }, rejected::incrementAndGet)
            );
            scheduler.execute(token(), () -> { }, rejected::incrementAndGet);
            assertEquals(2, rejected.get());
        }
    }

    @Test
    void submitsCompletionsImmediatelyWithoutAnotherGlobalCommandQueue() {
        try (var scheduler = new ScheduledMatchScheduler(2, System::nanoTime)) {
            var caller = Thread.currentThread();
            var submitted = new AtomicInteger();
            scheduler.execute(
                token(),
                () -> {
                    assertEquals(caller, Thread.currentThread());
                    submitted.incrementAndGet();
                },
                () -> { }
            );

            assertEquals(1, submitted.get());
            assertEquals(0, scheduler.pendingCount());
        }
    }

    @Test
    void cleansUpWhenTimerRegistrationFailsAndNeverDispatchesCancelledCommands() throws Exception {
        var timerRef = new AtomicReference<ScheduledThreadPoolExecutor>();

        try (var scheduler = new ScheduledMatchScheduler(
            1,
            () -> {
                timerRef.get().shutdownNow();
                return 0;
            }
        )) {
            var field = ScheduledMatchScheduler.class.getDeclaredField("timers");
            field.setAccessible(true);
            timerRef.set((ScheduledThreadPoolExecutor) field.get(scheduler));

            assertThrows(
                RejectedExecutionException.class,
                () -> scheduler.schedule(new MatchDeadline(token(), 0), () -> { }, () -> { })
            );
            assertEquals(0, scheduler.pendingCount());
            var count = new AtomicInteger();
            var cancelled = new ScheduledMatchTask(UUID.randomUUID(), count::incrementAndGet, ignored -> { });
            cancelled.cancel(false);
            var dispatch = ScheduledMatchScheduler.class.getDeclaredMethod("dispatch", ScheduledMatchTask.class);
            dispatch.setAccessible(true);
            dispatch.invoke(scheduler, cancelled);

            assertEquals(0, count.get());
        }
    }

    @Test
    void cancelsATimerAttachedAfterItsCommandCompletes() {
        var task = new ScheduledMatchTask(UUID.randomUUID(), () -> { }, ignored -> { });
        task.run();

        assertFalse(task.isCancelled());
        try (var timers = new ScheduledThreadPoolExecutor(1)) {
            var timer = timers.schedule(() -> { }, 1, TimeUnit.DAYS);
            task.timer(timer);

            assertTrue(timer.isCancelled());
        }
    }

    private RoomMatchProgressToken token() {
        return new RoomMatchProgressToken(new RoomMatchToken(new RoomId(1), UUID.randomUUID(), UUID.randomUUID()), 1);
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
