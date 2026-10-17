package taan.online.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.logging.WarningLogLimiter;

class ConnectionLimitHandlerTest {
    @Test
    void releasesSlotAfterFrameAssemblyTimeoutAndAcceptsAnotherConnection() {
        var permits = new Semaphore(1);
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(getClass()),
            1,
            Duration.ofSeconds(1),
            System::nanoTime
        );
        var first = new EmbeddedChannel(new ConnectionLimitHandler(permits, warnings), new FrameDecoder(1024, 10));
        first.freezeTime();
        first.writeInbound(Unpooled.wrappedBuffer(new byte[]{4}));

        assertEquals(0, permits.availablePermits());

        first.advanceTimeBy(10, TimeUnit.SECONDS);
        first.runScheduledPendingTasks();

        assertFalse(first.isActive());
        assertEquals(1, permits.availablePermits());

        var next = new EmbeddedChannel(new ConnectionLimitHandler(permits, warnings));

        assertTrue(next.isActive());

        first.finishAndReleaseAll();
        next.finishAndReleaseAll();

        assertEquals(1, permits.availablePermits());
    }

    @Test
    void rejectsExcessConnectionsAndReleasesPermitsExactlyOnce() {
        var permits = new Semaphore(1);
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(getClass()),
            1,
            Duration.ofSeconds(1),
            System::nanoTime
        );
        var first = new EmbeddedChannel(new ConnectionLimitHandler(permits, warnings));

        assertTrue(first.isActive());
        assertEquals(0, permits.availablePermits());

        var second = new EmbeddedChannel(new ConnectionLimitHandler(permits, warnings));

        assertFalse(second.isActive());

        second.finishAndReleaseAll();

        assertEquals(0, permits.availablePermits());

        first.finishAndReleaseAll();
        first.close();

        assertEquals(1, permits.availablePermits());

        var third = new EmbeddedChannel(new ConnectionLimitHandler(permits, warnings));

        assertTrue(third.isActive());

        third.finishAndReleaseAll();

        assertEquals(1, permits.availablePermits());
    }
}
