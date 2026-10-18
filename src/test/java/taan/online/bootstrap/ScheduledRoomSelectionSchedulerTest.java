package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.RoomSelectionToken;

class ScheduledRoomSelectionSchedulerTest {
    @Test
    void runsCommandsOutsideTimerThreadsAndCancelsQueuedAndDelayedWorkWithinItsBound() throws Exception {
        try (var scheduler = new ScheduledRoomSelectionScheduler(2)) {
            var running = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var finished = new CountDownLatch(1);
            var completed = new AtomicInteger();
            var first = token();
            MDC.put("operationId", "captain-test");
            scheduler.schedule(
                first,
                0,
                () -> {
                    assertTrue(Thread.currentThread().getName().startsWith("room-selection-command-"));
                    assertEquals("captain-test", MDC.get("operationId"));
                    running.countDown();
                    await(release);
                }
            );
            MDC.clear();
            assertTrue(running.await(5, TimeUnit.SECONDS));
            var cancelled = token();
            scheduler.schedule(cancelled, 0, completed::incrementAndGet);
            scheduler.schedule(token(), TimeUnit.DAYS.toNanos(1), completed::incrementAndGet);
            scheduler.schedule(
                token(),
                0,
                () -> {
                    assertEquals(null, MDC.get("operationId"));
                    finished.countDown();
                }
            );

            assertThrows(RejectedExecutionException.class, () -> scheduler.schedule(token(), 0, () -> { }));
            scheduler.cancel(cancelled.selectionId());
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(0, completed.get());
            scheduler.close();
            scheduler.close();
            assertThrows(RejectedExecutionException.class, () -> scheduler.schedule(token(), 0, () -> { }));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void safelyCancelsATaskWhoseDeadlineArrivesDuringExecutorShutdown() throws Exception {
        try (var scheduler = new ScheduledRoomSelectionScheduler(1)) {
            var field = ScheduledRoomSelectionScheduler.class.getDeclaredField("commands");
            field.setAccessible(true);
            ((ThreadPoolExecutor) field.get(scheduler)).shutdownNow();
            var done = new CountDownLatch(1);
            var task = new RoomSelectionTask(UUID.randomUUID(), () -> { }, ignored -> done.countDown());
            var dispatch = ScheduledRoomSelectionScheduler.class.getDeclaredMethod(
                "dispatch",
                RoomSelectionTask.class
            );
            dispatch.setAccessible(true);
            dispatch.invoke(scheduler, task);

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(task.isCancelled());
        }
    }

    @Test
    void cancelsATimerAttachedAfterItsCommandAlreadyFinished() {
        var count = new AtomicInteger();
        var task = new RoomSelectionTask(UUID.randomUUID(), () -> { }, ignored -> count.incrementAndGet());
        task.run();
        assertEquals(1, count.get());
        assertFalse(task.isCancelled());

        try (var executor = new ScheduledThreadPoolExecutor(1)) {
            var timer = executor.schedule(() -> { }, 1, TimeUnit.DAYS);
            task.timer(timer);

            assertTrue(timer.isCancelled());
        }
    }

    @Test
    void neverEnqueuesCancelledCommandsWhenCancellationRacesWithTimerDispatch() throws Exception {
        try (var scheduler = new ScheduledRoomSelectionScheduler(1)) {
            var running = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            scheduler.schedule(
                token(),
                0,
                () -> {
                    running.countDown();
                    await(release);
                }
            );
            assertTrue(running.await(5, TimeUnit.SECONDS));

            try {
                for (int index = 0; index < 100; index++) {
                    var cancelled = token();
                    scheduler.schedule(cancelled, 0, () -> { });
                    scheduler.cancel(cancelled.selectionId());
                }

                var timersField = ScheduledRoomSelectionScheduler.class.getDeclaredField("timers");
                timersField.setAccessible(true);
                var timers = (ScheduledThreadPoolExecutor) timersField.get(scheduler);
                timers.submit(() -> { }).get(5, TimeUnit.SECONDS);
                var commandsField = ScheduledRoomSelectionScheduler.class.getDeclaredField("commands");
                commandsField.setAccessible(true);
                var commands = (ThreadPoolExecutor) commandsField.get(scheduler);

                assertTrue(commands.getQueue().isEmpty());
                var done = new RoomSelectionTask(UUID.randomUUID(), () -> { }, ignored -> { });
                done.cancel(false);
                var dispatch = ScheduledRoomSelectionScheduler.class.getDeclaredMethod(
                    "dispatch",
                    RoomSelectionTask.class
                );
                dispatch.setAccessible(true);
                dispatch.invoke(scheduler, done);
                assertTrue(commands.getQueue().isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    private RoomSelectionToken token() {
        return new RoomSelectionToken(new RoomId(1), UUID.randomUUID(), UUID.randomUUID());
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
