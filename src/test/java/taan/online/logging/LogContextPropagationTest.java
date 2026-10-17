package taan.online.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogContextPropagationTest {
    @Test
    void propagatesQueuedWorkAndRestoresTheReceivingThreadsContextEvenWhenWorkFails() {
        Runnable task;

        try (var scope = new LogContext("source", "operation", "session")) {
            task = LogContext.propagate(
                () -> {
                    assertEquals("source", MDC.get("connectionId"));
                    assertEquals("operation", MDC.get("operationId"));
                    assertEquals("session", MDC.get("sessionId"));
                    throw new IllegalStateException("Test failure");
                }
            );
        }

        try (var scope = new LogContext("receiver", "other")) {
            assertThrows(IllegalStateException.class, task::run);
            assertEquals("receiver", MDC.get("connectionId"));
            assertNull(MDC.get("sessionId"));
        }

        assertNull(MDC.get("connectionId"));
        Runnable empty = LogContext.propagate(() -> assertNull(MDC.get("connectionId")));

        try (var scope = new LogContext("receiver", "other")) {
            empty.run();

            assertEquals("receiver", MDC.get("connectionId"));
        }

        empty.run();

        assertNull(MDC.get("connectionId"));
    }
}
