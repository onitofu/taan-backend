package taan.online.match.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import taan.online.logging.LogContext;

final class MatchMailboxTask implements Runnable {
    private final Runnable command;
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();

    MatchMailboxTask(BooleanSupplier command) {
        this.command = LogContext.propagate(
            () -> {
                try {
                    result.complete(command.getAsBoolean());
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            }
        );
    }

    CompletableFuture<Boolean> result() {
        return result;
    }

    void cancel() {
        result.complete(false);
    }

    @Override
    public void run() {
        command.run();
    }
}
