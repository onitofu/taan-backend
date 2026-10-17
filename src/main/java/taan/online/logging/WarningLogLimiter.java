package taan.online.logging;

import java.time.Duration;
import java.util.EnumMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

public final class WarningLogLimiter {
    private final Logger logger;
    private final int burst;
    private final long intervalNanos;
    private final LongSupplier clock;
    private final EnumMap<WarningEvent, WarningWindow> windows = new EnumMap<>(WarningEvent.class);

    public WarningLogLimiter(Logger logger, int burst, Duration interval, LongSupplier clock) {
        this.logger = logger;
        this.burst = burst;
        this.intervalNanos = interval.toNanos();
        this.clock = clock;
    }

    public void warn(WarningEvent event, String reason) {
        warn(event, reason, null);
    }

    public synchronized void warn(WarningEvent event, String reason, String connectionId) {
        long now = clock.getAsLong();
        WarningWindow window = windows.computeIfAbsent(event, ignored -> new WarningWindow(now));

        if (now - window.started() >= intervalNanos) {
            if (window.suppressed() > 0) {
                logger.atWarn().addKeyValue("event", event.eventName())
                    .addKeyValue("suppressedCount", window.suppressed())
                    .log("Repeated warnings suppressed");
            }

            window.reset(now);
        }

        if (window.emitted() < burst) {
            window.recordEmission();
            var entry = logger.atWarn().addKeyValue("event", event.eventName()).addKeyValue("reason", reason);

            if (connectionId != null) {
                entry.addKeyValue("connectionId", connectionId);
            }

            entry.log("Request rejected");
        } else {
            window.recordSuppression();
        }
    }
}
