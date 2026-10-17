package taan.online.logging;

import java.util.Map;
import org.slf4j.MDC;

public final class LogContext implements AutoCloseable {
    private final Map<String, String> previous;

    public LogContext(String connectionId, String operationId) {
        this(connectionId, operationId, null);
    }

    public LogContext(String connectionId, String operationId, String sessionId) {
        previous = MDC.getCopyOfContextMap();
        MDC.clear();
        MDC.put("connectionId", connectionId);
        MDC.put("operationId", operationId);

        if (sessionId != null) {
            MDC.put("sessionId", sessionId);
        }
    }

    public static Runnable propagate(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.clear();

            if (captured != null) {
                MDC.setContextMap(captured);
            }

            try {
                task.run();
            } finally {
                MDC.clear();

                if (previous != null) {
                    MDC.setContextMap(previous);
                }
            }
        };
    }

    @Override
    public void close() {
        MDC.clear();

        if (previous != null) {
            MDC.setContextMap(previous);
        }
    }
}
