package taan.online.logging;

import org.slf4j.event.Level;

public interface LogEvent {
    String eventName();

    Level level();

    String message();
}
