package taan.online.channel.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum ChannelLogEvent implements LogEvent {
    REQUEST_CANCELLED("channel.request.cancelled", Level.DEBUG, "Channel request cancelled"),
    LIST_LOADED("channel.list.loaded", Level.DEBUG, "Channel list loaded"),
    LIST_FAILED("channel.list.failed", Level.ERROR, "Channel list failed"),
    MOVE_SUCCEEDED("channel.move.succeeded", Level.INFO, "Channel selected"),
    MOVE_REJECTED("channel.move.rejected", Level.INFO, "Channel entry rejected"),
    MOVE_FAILED("channel.move.failed", Level.ERROR, "Channel entry failed"),
    MEMBERSHIP_RELEASED("channel.membership.released", Level.DEBUG, "Channel membership released");

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
