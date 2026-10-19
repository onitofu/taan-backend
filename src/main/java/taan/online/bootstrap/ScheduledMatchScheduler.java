package taan.online.bootstrap;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import taan.online.logging.LogContext;
import taan.online.match.service.MatchScheduler;
import taan.online.match.service.model.MatchDeadline;
import taan.online.room.service.model.RoomMatchProgressToken;

public final class ScheduledMatchScheduler implements MatchScheduler, AutoCloseable {
    private final Set<ScheduledMatchTask> pending = new HashSet<>();
    private final ScheduledThreadPoolExecutor timers;
    private final int capacity;
    private final LongSupplier clock;

    public ScheduledMatchScheduler(int maxConnections, LongSupplier clock) {
        capacity = Math.multiplyExact(2, maxConnections);
        this.clock = clock;
        timers = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon(true).name("match-timer-", 0).factory());
        timers.setRemoveOnCancelPolicy(true);
    }

    @Override
    public synchronized void schedule(MatchDeadline deadline, Runnable command, Runnable rejected) {
        var task = task(deadline.token(), command, rejected);

        try {
            task.timer(
                timers.schedule(
                    () -> dispatch(task),
                    Math.max(0, deadline.deadlineNanos() - clock.getAsLong()),
                    TimeUnit.NANOSECONDS
                )
            );
        } catch (RejectedExecutionException failure) {
            task.cancel(false);
            throw failure;
        }
    }

    @Override
    public void execute(RoomMatchProgressToken token, Runnable command, Runnable rejected) {
        ScheduledMatchTask task;

        synchronized (this) {
            if (timers.isShutdown() || pending.size() >= capacity) {
                task = null;
            } else {
                task = task(token, command, rejected);
            }
        }

        if (task == null) {
            rejected.run();
        } else {
            dispatch(task);
        }
    }

    private ScheduledMatchTask task(RoomMatchProgressToken token, Runnable command, Runnable rejected) {
        if (timers.isShutdown() || pending.size() >= capacity) {
            throw new RejectedExecutionException("Match scheduler is unavailable");
        }

        var task = new ScheduledMatchTask(token.match().matchId(), LogContext.propagate(command), this::completed);
        pending.add(task);
        return task;
    }

    private void dispatch(ScheduledMatchTask task) {
        task.run();
    }

    @Override
    public synchronized void cancel(UUID matchId) {
        pending.stream().filter(task -> task.matchId().equals(matchId)).toList()
            .forEach(task -> task.cancel(false));
    }

    private synchronized void completed(ScheduledMatchTask task) {
        pending.remove(task);
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    @Override
    public synchronized void close() {
        Set.copyOf(pending).forEach(task -> task.cancel(false));
        timers.shutdownNow();
    }
}
