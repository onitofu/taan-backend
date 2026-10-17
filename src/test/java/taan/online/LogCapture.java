package taan.online;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class LogCapture implements AutoCloseable {
    private final Logger logger;
    private final Level previous;
    private final List<String> lines = new ArrayList<>();
    private final List<ILoggingEvent> events = new ArrayList<>();
    private final AppenderBase<ILoggingEvent> appender;

    public LogCapture() {
        this("taan.online");
    }

    public LogCapture(String name) {
        logger = (Logger) LoggerFactory.getLogger(name);
        previous = logger.getLevel();

        var layout = new PatternLayout();
        layout.setContext(logger.getLoggerContext());
        layout.setPattern("%level %mdc %kvp %msg %ex");
        layout.start();

        appender = new CapturingLogAppender(lines, events, layout);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.TRACE);
    }

    public String text() {
        return String.join("\n", lines);
    }

    public int size() {
        return lines.size();
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previous);
    }
}
