package taan.online.bootstrap;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import taan.online.match.service.MatchCommandExecutor;

public final class BoundedMatchExecutor implements MatchCommandExecutor, AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final int quantum;

    public BoundedMatchExecutor(int workers, int maxConnections) {
        this(workers, maxConnections, 32);
    }

    public BoundedMatchExecutor(int workers, int maxConnections, int quantum) {
        this.quantum = quantum;
        executor = new ThreadPoolExecutor(
            workers,
            workers,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(maxConnections),
            Thread.ofPlatform().daemon(true).name("match-command-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public int quantum() {
        return quantum;
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    public boolean awaitTermination(long timeoutNanos) throws InterruptedException {
        executor.shutdown();
        return executor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow().forEach(Runnable::run);
    }
}
