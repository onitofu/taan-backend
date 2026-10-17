package taan.online.channel.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum ChannelLogEvent implements LogEvent {
    LIST_LOADED("channel.list.loaded", Level.DEBUG, "Channel list loaded"),
    LIST_FAILED("channel.list.failed", Level.ERROR, "Channel list failed");

    private final String eventName;
    private final Level level;
    private final String message;

    ChannelLogEvent(String eventName, Level level, String message) {
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
