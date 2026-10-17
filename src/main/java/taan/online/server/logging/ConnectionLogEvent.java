package taan.online.server.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum ConnectionLogEvent implements LogEvent {
    OPENED("connection.opened", Level.DEBUG, "Connection opened"),
    CLOSED("connection.closed", Level.DEBUG, "Connection closed"),
    FAILED("connection.failed", Level.ERROR, "Connection failed"),
    PACKET_RECEIVED("protocol.packet.received", Level.TRACE, "Packet received");

    private final String eventName;
    private final Level level;
    private final String message;

    ConnectionLogEvent(String eventName, Level level, String message) {
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
