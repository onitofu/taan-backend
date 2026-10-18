package taan.online.bootstrap;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import taan.online.logging.LogContext;
import taan.online.room.service.RoomSelectionScheduler;
import taan.online.room.service.model.RoomSelectionToken;

public final class ScheduledRoomSelectionScheduler implements RoomSelectionScheduler, AutoCloseable {
    private final Set<RoomSelectionTask> pending = new HashSet<>();
    private final ScheduledThreadPoolExecutor timers;
    private final ThreadPoolExecutor commands;
    private final int capacity;

    public ScheduledRoomSelectionScheduler(int maxConnections) {
        capacity = Math.multiplyExact(2, maxConnections);
        commands = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(capacity),
            Thread.ofPlatform().daemon(true).name("room-selection-command-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
        timers = new ScheduledThreadPoolExecutor(
            1,
            Thread.ofPlatform().daemon(true).name("room-selection-timer-", 0).factory()
        );
        timers.setRemoveOnCancelPolicy(true);
    }

    @Override
    public synchronized void schedule(RoomSelectionToken token, long delayNanos, Runnable command) {
        if (timers.isShutdown() || pending.size() >= capacity) {
            throw new RejectedExecutionException("Room selection scheduler is unavailable");
        }

        var task = new RoomSelectionTask(token.selectionId(), LogContext.propagate(command), this::completed);
        pending.add(task);
        task.timer(timers.schedule(() -> dispatch(task), delayNanos, TimeUnit.NANOSECONDS));
    }

    @Override
    public synchronized void cancel(UUID selectionId) {
        pending.stream().filter(task -> task.selectionId().equals(selectionId)).toList()
            .forEach(task -> task.cancel(false));
    }

    private synchronized void dispatch(RoomSelectionTask task) {
        if (task.isCancelled()) {
            return;
        }

        try {
            commands.execute(task);
        } catch (RejectedExecutionException failure) {
            task.cancel(false);
        }
    }

    private synchronized void completed(RoomSelectionTask task) {
        commands.remove(task);
        pending.remove(task);
    }

    @Override
    public synchronized void close() {
        Set.copyOf(pending).forEach(task -> task.cancel(false));
        timers.shutdownNow();
        commands.shutdownNow();
    }
}
