package taan.online.match.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class MatchMailbox {
    private final ArrayDeque<MatchMailboxTask<?>> pending = new ArrayDeque<>();
    private final ArrayDeque<MatchMailboxTask<?>> control = new ArrayDeque<>();
    private final MatchCommandExecutor executor;
    private final int capacity;
    private boolean scheduled;
    private MatchMailboxPhase phase = MatchMailboxPhase.OPEN;
    private MatchMailboxTask<Boolean> terminal;
    private Runnable onRejected;

    MatchMailbox(MatchCommandExecutor executor, int capacity) {
        if (capacity < 1 || executor.quantum() < 1) {
            throw new IllegalArgumentException("Match queue capacity and quantum must be positive");
        }

        this.executor = executor;
        this.capacity = capacity;
    }

    CompletableFuture<Boolean> submit(BooleanSupplier command, Runnable rejected) {
        return submit(command::getAsBoolean, false, rejected);
    }

    <T> CompletableFuture<T> submit(Supplier<T> command, T cancelled, Runnable rejected) {
        return enqueue(command, cancelled, rejected, false);
    }

    <T> CompletableFuture<T> control(Supplier<T> command, T cancelled, Runnable rejected) {
        return enqueue(command, cancelled, rejected, true);
    }

    private <T> CompletableFuture<T> enqueue(
        Supplier<T> command,
        T cancelled,
        Runnable rejected,
        boolean internal
    ) {
        var task = new MatchMailboxTask<>(command, cancelled);
        boolean schedule = false;
        boolean overflow = false;
        boolean stopped;

        synchronized (this) {
            stopped = phase == MatchMailboxPhase.CLOSED || !internal && phase == MatchMailboxPhase.DRAINING;

            if (!stopped) {
                var queue = internal ? control : pending;
                overflow = queue.size() >= capacity;

                if (!overflow) {
                    queue.addLast(task);
                    schedule = !scheduled;
                    scheduled = true;
                    onRejected = rejected;
                }
            }
        }

        if (stopped || overflow) {
            task.cancel();

            if (overflow) {
                rejected.run();
            }
        } else if (schedule) {
            schedule();
        }

        return task.result();
    }

    void fence() {
        List<MatchMailboxTask<?>> cancelled;

        synchronized (this) {
            if (phase != MatchMailboxPhase.OPEN) {
                return;
            }

            phase = MatchMailboxPhase.DRAINING;
            cancelled = List.copyOf(pending);
            pending.clear();
        }

        cancelled.forEach(MatchMailboxTask::cancel);
    }

    CompletableFuture<Boolean> terminate(BooleanSupplier command) {
        List<MatchMailboxTask<?>> cancelled;
        boolean schedule;
        MatchMailboxTask<Boolean> task;

        synchronized (this) {
            if (terminal != null) {
                return terminal.result();
            }

            phase = MatchMailboxPhase.CLOSED;
            cancelled = new ArrayList<>(pending);
            cancelled.addAll(control);
            pending.clear();
            control.clear();
            task = new MatchMailboxTask<>(command::getAsBoolean, false);
            terminal = task;
            schedule = !scheduled;
            scheduled = true;
        }

        cancelled.forEach(MatchMailboxTask::cancel);

        if (schedule) {
            schedule();
        }

        return task.result();
    }

    private void schedule() {
        try {
            executor.execute(this::run);
        } catch (RejectedExecutionException failure) {
            List<MatchMailboxTask<?>> cancelled;
            MatchMailboxTask<Boolean> ending;
            Runnable rejected;

            synchronized (this) {
                scheduled = false;
                cancelled = new ArrayList<>(pending);
                cancelled.addAll(control);
                pending.clear();
                control.clear();
                ending = terminal;
                rejected = onRejected;
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
        for (int count = 0; count < executor.quantum(); count++) {
            MatchMailboxTask<?> work;

            synchronized (this) {
                work = control.pollFirst();

                if (work == null) {
                    work = pending.pollFirst();
                }

                if (work == null) {
                    work = terminal;

                    if (work == null) {
                        scheduled = false;
                        return;
                    }
                }
            }

            work.run();

            if (work == terminal) {
                return;
            }
        }

        synchronized (this) {
            if (pending.isEmpty() && control.isEmpty() && terminal == null) {
                scheduled = false;
                return;
            }
        }

        schedule();
    }
}
