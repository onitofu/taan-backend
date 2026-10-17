package taan.online;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;

final class CapturingLogAppender extends AppenderBase<ILoggingEvent> {
    private final List<String> lines;
    private final List<ILoggingEvent> events;
    private final PatternLayout layout;

    CapturingLogAppender(List<String> lines, List<ILoggingEvent> events, PatternLayout layout) {
        this.lines = lines;
        this.events = events;
        this.layout = layout;
    }

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        events.add(event);
        lines.add(layout.doLayout(event));
    }
}
