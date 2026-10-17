package taan.online.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PromiseCombiner;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.protocol.PacketOutput;

final class ConnectionPacketOutput implements PacketOutput {
    private final ChannelHandlerContext context;
    private final WarningLogLimiter warnings;
    private final UUID connectionId;
    private final int maxBatches;
    private final int maxBytes;
    private final int maxFrame;
    private final Consumer<String> closer;
    private final ArrayDeque<OutboundBatch> pending = new ArrayDeque<>();
    private OutputPhase phase = OutputPhase.OPEN;
    private long bytes;

    ConnectionPacketOutput(
        ChannelHandlerContext context,
        WarningLogLimiter warnings,
        UUID connectionId,
        int maxBatches,
        int maxBytes,
        int maxFrame,
        Consumer<String> closer
    ) {
        this.context = context;
        this.warnings = warnings;
        this.connectionId = connectionId;
        this.maxBatches = maxBatches;
        this.maxBytes = maxBytes;
        this.maxFrame = maxFrame;
        this.closer = closer;
    }

    @Override
    public synchronized CompletionStage<Void> send(List<byte[]> roots) {
        if (phase == OutputPhase.CLOSED) {
            return CompletableFuture.failedFuture(new IOException("Connection output closed"));
        }

        if (roots.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var batch = new OutboundBatch(roots, new CompletableFuture<>());

        if (pending.size() >= maxBatches || batch.bytes() > maxBytes - bytes
            || roots.stream().anyMatch(root -> root.length > maxFrame)) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "OUTPUT_QUEUE_FULL", connectionId.toString());
            batch.delivery().completeExceptionally(new IOException("Connection output limit exceeded"));
            fail("OUTPUT_QUEUE_FULL");
            return batch.delivery();
        }

        pending.addLast(batch);
        bytes += batch.bytes();
        schedule(this::drain);
        return batch.delivery();
    }

    @Override
    public void close(String reason) {
        schedule(() -> closer.accept(reason));
    }

    synchronized void close() {
        phase = OutputPhase.CLOSED;
        bytes = 0;

        while (!pending.isEmpty()) {
            pending.removeFirst().delivery().completeExceptionally(new IOException("Connection output closed"));
        }
    }

    private void drain() {
        OutboundBatch batch;

        synchronized (this) {
            if (phase != OutputPhase.OPEN || pending.isEmpty()) {
                return;
            }

            phase = OutputPhase.WRITING;
            batch = pending.getFirst();
        }

        var combined = context.newPromise();
        var promises = new PromiseCombiner(context.executor());

        for (byte[] root : batch.roots()) {
            promises.add((Future<?>) context.write(root));
        }

        promises.finish(combined);
        combined.addListener(result -> finished(batch, result.isSuccess()));
        context.flush();
    }

    private void finished(OutboundBatch batch, boolean success) {
        synchronized (this) {
            if (phase == OutputPhase.CLOSED) {
                return;
            }

            if (success) {
                pending.removeFirst();
                bytes -= batch.bytes();
                phase = OutputPhase.OPEN;
            }
        }

        if (!success) {
            fail("WRITE_FAILED");
            return;
        }

        batch.delivery().complete(null);
        drain();
    }

    private void fail(String reason) {
        close();
        schedule(() -> closer.accept(reason));
    }

    private void schedule(Runnable action) {
        try {
            context.executor().execute(LogContext.propagate(action));
        } catch (RejectedExecutionException failure) {
            close();
            context.channel().close();
        }
    }
}
