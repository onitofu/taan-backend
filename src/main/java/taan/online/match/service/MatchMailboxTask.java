package taan.online.match.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import taan.online.logging.LogContext;

final class MatchMailboxTask<T> implements Runnable {
    private final Runnable command;
    private final T cancelled;
    private final CompletableFuture<T> result = new CompletableFuture<>();

    MatchMailboxTask(Supplier<T> command, T cancelled) {
        this.cancelled = cancelled;
        this.command = LogContext.propagate(
            () -> {
                try {
                    result.complete(command.get());
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            }
        );
    }

    CompletableFuture<T> result() {
        return result;
    }

    void cancel() {
        result.complete(cancelled);
    }

    @Override
    public void run() {
        command.run();
    }
}
