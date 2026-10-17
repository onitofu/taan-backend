package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.FrameEncoder;

class ConnectionPacketOutputTest {
    @Test
    void postsExplicitClosureToTheConnectionOwnerInsteadOfCleaningUpInsideTheCaller() {
        var channel = new EmbeddedChannel(new FrameEncoder(), new ChannelInboundHandlerAdapter());
        var reasons = new ArrayList<String>();

        try {
            var output = output(channel, 64, 1024, 128, reasons);
            output.close("LOBBY_NOTIFICATION_FAILED");

            assertTrue(reasons.isEmpty());
            channel.runPendingTasks();

            assertEquals(List.of("LOBBY_NOTIFICATION_FAILED"), reasons);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void keepsBatchesContiguousAndCompletesReceiptsOnlyAfterEveryWriteFlushes() {
        var delayed = new QueuedOutboundWrites();

        var channel = new EmbeddedChannel(new FrameEncoder(), delayed, new ChannelInboundHandlerAdapter());

        try {
            var output = output(channel, 64, 1024, 128, new ArrayList<>());
            byte[] root = {1, 2, 3, 4};
            var snapshot = output.send(List.of(root, new byte[]{5, 6, 7, 8})).toCompletableFuture();
            root[0] = 99;
            var delta = output.send(List.of(new byte[]{9, 10, 11, 12})).toCompletableFuture();
            channel.runPendingTasks();

            assertFalse(snapshot.isDone());
            assertFalse(delta.isDone());

            delayed.send();

            assertArrayEquals(new byte[]{1, 2, 3, 4}, read(channel));
            assertFalse(snapshot.isDone());

            delayed.send();

            assertArrayEquals(new byte[]{5, 6, 7, 8}, read(channel));
            assertTrue(snapshot.isDone());
            assertFalse(delta.isDone());

            delayed.send();

            assertArrayEquals(new byte[]{9, 10, 11, 12}, read(channel));
            assertTrue(delta.isDone());
            assertTrue(output.send(List.of()).toCompletableFuture().isDone());

            output.close();

            assertTrue(output.send(List.of(root)).toCompletableFuture().isCompletedExceptionally());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void boundsQueuedBatchCountBytesAndIndividualFramesAndClosesTheSlowConnection() {
        for (int mode = 0; mode < 3; mode++) {
            var reasons = new ArrayList<String>();
            var delayed = new QueuedOutboundWrites();

            var channel = new EmbeddedChannel(new FrameEncoder(), delayed, new ChannelInboundHandlerAdapter());

            try {
                var output = output(channel, mode == 0 ? 1 : 64, mode == 1 ? 1 : 1024, mode == 2 ? 3 : 128, reasons);
                var first = output.send(List.of(new byte[4])).toCompletableFuture();
                channel.runPendingTasks();
                var rejected = output.send(List.of(new byte[4])).toCompletableFuture();
                channel.runPendingTasks();

                assertTrue(first.isCompletedExceptionally());
                assertTrue(rejected.isCompletedExceptionally());
                assertEquals(List.of("OUTPUT_QUEUE_FULL"), reasons);
                assertFalse(channel.isActive());

                if (mode == 0) {
                    delayed.succeed();
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void failsEveryPendingReceiptOnWriteFailureClosureOrExecutorRejection() {
        var reasons = new ArrayList<String>();
        var delayed = new QueuedOutboundWrites();

        var channel = new EmbeddedChannel(new FrameEncoder(), delayed, new ChannelInboundHandlerAdapter());

        try {
            var output = output(channel, 64, 1024, 128, reasons);
            var first = output.send(List.of(new byte[4])).toCompletableFuture();
            var next = output.send(List.of(new byte[4])).toCompletableFuture();
            channel.runPendingTasks();
            delayed.fail();
            channel.runPendingTasks();

            assertTrue(first.isCompletedExceptionally());
            assertTrue(next.isCompletedExceptionally());
            assertEquals(List.of("WRITE_FAILED"), reasons);
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }

        var rejectedChannel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

        try {
            var output = new ConnectionPacketOutput(
                RejectingChannelContext.wrap(rejectedChannel.pipeline().lastContext()),
                warnings(),
                UUID.randomUUID(),
                64,
                1024,
                128,
                ignored -> rejectedChannel.close()
            );
            var receipt = output.send(List.of(new byte[4])).toCompletableFuture();

            assertTrue(receipt.isCompletedExceptionally());
            assertFalse(rejectedChannel.isActive());
        } finally {
            rejectedChannel.finishAndReleaseAll();
        }
    }

    private ConnectionPacketOutput output(
        EmbeddedChannel channel,
        int batches,
        int bytes,
        int frame,
        List<String> reasons
    ) {
        var output = new ConnectionPacketOutput(
            channel.pipeline().lastContext(),
            warnings(),
            UUID.randomUUID(),
            batches,
            bytes,
            frame,
            reason -> {
                reasons.add(reason);
                channel.close();
            }
        );
        channel.closeFuture().addListener(ignored -> output.close());
        return output;
    }

    private WarningLogLimiter warnings() {
        return new WarningLogLimiter(
            LoggerFactory.getLogger(ConnectionPacketOutputTest.class),
            10,
            Duration.ofMinutes(1),
            System::nanoTime
        );
    }

    private byte[] read(EmbeddedChannel channel) {
        ByteBuf buffer = channel.readOutbound();
        byte[] root = new byte[buffer.readIntLE()];
        buffer.readBytes(root);
        buffer.release();
        return root;
    }
}
