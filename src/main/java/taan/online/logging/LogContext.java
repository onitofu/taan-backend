package taan.online.logging;

import java.util.Map;
import org.slf4j.MDC;

public final class LogContext implements AutoCloseable {
    private final Map<String, String> previous;

    public LogContext(String connectionId, String operationId) {
        previous = MDC.getCopyOfContextMap();
        MDC.clear();
        MDC.put("connectionId", connectionId);
        MDC.put("operationId", operationId);
    }

    @Override
    public void close() {
        MDC.clear();

        if (previous != null) {
            MDC.setContextMap(previous);
        }
    }
}
