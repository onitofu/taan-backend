package taan.online.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventLogger {
    private final Logger logger;

    private EventLogger(Logger logger) {
        this.logger = logger;
    }

    public static EventLogger forClass(Class<?> source) {
        return new EventLogger(LoggerFactory.getLogger(source));
    }

    public void log(LogEvent event, LogField... fields) {
        write(event, null, fields);
    }

    public void failure(LogEvent event, Throwable failure, LogField... fields) {
        write(event, failure, fields);
    }

    private void write(LogEvent event, Throwable failure, LogField[] fields) {
        if (!logger.isEnabledForLevel(event.level())) {
            return;
        }

        var entry = logger.atLevel(event.level()).addKeyValue("event", event.eventName());

        for (LogField field : fields) {
            field.addTo(entry);
        }

        if (failure != null) {
            entry.setCause(new SafeLogException(failure));
        }

        entry.log(event.message());
    }
}
