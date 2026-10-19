package taan.online.match.service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class MatchMailbox {
    private final ArrayDeque<MatchMailboxTask<?>> pending = new ArrayDeque<>();
    private final MatchCommandExecutor executor;
    private final int capacity;
    private boolean scheduled;
    private boolean closed;
    private MatchMailboxTask<Boolean> terminal;

    MatchMailbox(MatchCommandExecutor executor, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Match queue capacity must be positive");
        }

        this.executor = executor;
        this.capacity = capacity;
    }

    CompletableFuture<Boolean> submit(BooleanSupplier command, Runnable rejected) {
        return submit(command::getAsBoolean, false, rejected);
    }

    <T> CompletableFuture<T> submit(Supplier<T> command, T cancelled, Runnable rejected) {
        var task = new MatchMailboxTask<>(command, cancelled);
        boolean schedule = false;
        boolean overflow = false;
        boolean stopped;

        synchronized (this) {
            stopped = closed;

            if (!stopped) {
                overflow = pending.size() >= capacity;

                if (!overflow) {
                    pending.addLast(task);
                    schedule = !scheduled;
                    scheduled = true;
                }
            }
        }

        if (stopped || overflow) {
            task.cancel();

            if (overflow) {
                rejected.run();
            }
        } else if (schedule) {
            schedule(rejected);
        }

        return task.result();
    }

    CompletableFuture<Boolean> terminate(BooleanSupplier command) {
        List<MatchMailboxTask<?>> cancelled;
        boolean schedule;
        MatchMailboxTask<Boolean> task;

        synchronized (this) {
            if (terminal != null) {
                return terminal.result();
            }

            closed = true;
            cancelled = List.copyOf(pending);
            pending.clear();
            task = new MatchMailboxTask<>(command::getAsBoolean, false);
            terminal = task;
            schedule = !scheduled;
            scheduled = true;
        }

        cancelled.forEach(MatchMailboxTask::cancel);

        if (schedule) {
            schedule(() -> { });
        }

        return task.result();
    }

    private void schedule(Runnable rejected) {
        try {
            executor.execute(this::run);
        } catch (RejectedExecutionException failure) {
            List<MatchMailboxTask<?>> cancelled;
            MatchMailboxTask<Boolean> ending;

            synchronized (this) {
                scheduled = false;
                cancelled = List.copyOf(pending);
                pending.clear();
                ending = terminal;
            }

            cancelled.forEach(MatchMailboxTask::cancel);

            if (ending != null) {
                ending.run();
            } else {
                rejected.run();
            }
        }
    }

    private void run() {
        while (true) {
            MatchMailboxTask<?> work;

            synchronized (this) {
                work = pending.pollFirst();

                if (work == null) {
                    work = terminal;
                    scheduled = false;
                }
            }

            if (work == null) {
                return;
            }

            work.run();

            if (work == terminal) {
                return;
            }
        }
    }
}
