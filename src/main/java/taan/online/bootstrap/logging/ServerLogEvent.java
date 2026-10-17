package taan.online.bootstrap.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum ServerLogEvent implements LogEvent {
    STARTING("server.starting", Level.INFO, "Server starting"),
    READY("server.ready", Level.INFO, "Server ready"),
    STOPPING("server.stopping", Level.INFO, "Server stopping"),
    STOPPED("server.stopped", Level.INFO, "Server stopped"),
    SHUTDOWN_TIMEOUT("server.shutdown.timeout", Level.WARN, "Shutdown deadline exceeded"),
    INVALID_CONFIGURATION("server.startup.failed", Level.ERROR, "Invalid configuration"),
    STARTUP_FAILED("server.startup.failed", Level.ERROR, "Server startup or execution failed");

    private final String eventName;
    private final Level level;
    private final String message;

    ServerLogEvent(String eventName, Level level, String message) {
        this.eventName = eventName;
        this.level = level;
        this.message = message;
    }

    @Override
    public String eventName() {
        return eventName;
    }

    @Override
    public Level level() {
        return level;
    }

    @Override
    public String message() {
        return message;
    }
}
