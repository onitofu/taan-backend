package taan.online.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import taan.online.LogCapture;

class LoggingTest {
    @Test
    void scopesAndRestoresContextAcrossOperations() {
        MDC.clear();

        try (var outer = new LogContext("connection-1", "operation-1")) {
            assertEquals("connection-1", MDC.get("connectionId"));

            try (var inner = new LogContext("connection-2", "operation-2")) {
                assertEquals("operation-2", MDC.get("operationId"));
            }

            assertEquals("operation-1", MDC.get("operationId"));
        }

        assertNull(MDC.get("connectionId"));
    }

    @Test
    void boundsWarningsAndReportsSuppressedCountsWithoutUnboundedKeys() {
        try (var logs = new LogCapture()) {
            var clock = new AtomicLong();
            var limiter = new WarningLogLimiter(
                LoggerFactory.getLogger(LoggingTest.class),
                1,
                Duration.ofSeconds(1),
                clock::get
            );
            limiter.warn(WarningEvent.FRAME_REJECTED, "BAD_LENGTH");
            limiter.warn(WarningEvent.FRAME_REJECTED, "BAD_LENGTH");
            limiter.warn(WarningEvent.FRAME_REJECTED, "BAD_LENGTH");

            assertEquals(1, logs.size());

            clock.set(Duration.ofSeconds(1).toNanos());
            limiter.warn(WarningEvent.FRAME_REJECTED, "BAD_LENGTH");

            assertEquals(3, logs.size());
            assertTrue(logs.text().contains("suppressedCount=\"2\""));

            clock.set(Duration.ofSeconds(2).toNanos());
            limiter.warn(WarningEvent.FRAME_REJECTED, "BAD_LENGTH");

            assertEquals(4, logs.size());

            limiter.warn(WarningEvent.LIMIT_EXCEEDED, "LIMIT", "connection-3");

            assertEquals(5, logs.size());
            assertTrue(logs.text().contains("connection-3"));
        }
    }

    @Test
    void retainsExceptionTypeAndFramesWithoutMessagesOrCauseSecrets() {
        var original = new IllegalStateException("secret-login", new RuntimeException("secret-password"));
        var safe = new SafeLogException(original);

        assertEquals(IllegalStateException.class.getName(), safe.getMessage());
        assertEquals(original.getStackTrace()[0], safe.getStackTrace()[0]);
        assertEquals(RuntimeException.class.getName(), safe.getCause().getMessage());

        try (var logs = new LogCapture()) {
            LoggerFactory.getLogger(LoggingTest.class).error("Operation failed", safe);

            assertFalse(logs.text().contains("secret-login"));
            assertFalse(logs.text().contains("secret-password"));
            assertTrue(logs.text().contains("java.lang.IllegalStateException"));
        }
    }

    @Test
    void boundsCauseDepthAndPreservesAlreadySanitizedDiagnostics() {
        Throwable failure = new RuntimeException("secret");

        for (int index = 0; index < 8; index++) {
            failure = new IllegalStateException("secret", failure);
        }

        Throwable safe = new SafeLogException(new SafeLogException(failure));
        int depth = 0;

        while (safe != null) {
            assertFalse(safe.getMessage().contains("secret"));

            safe = safe.getCause();
            depth++;
        }

        assertEquals(5, depth);
    }
}
