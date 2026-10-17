package taan.online.room.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum RoomLogEvent implements LogEvent {
    ENTRY_REJECTED("room.entry.rejected", Level.DEBUG, "Room entry rejected"),
    CREATION_SUCCEEDED("room.creation.succeeded", Level.INFO, "Room created"),
    CREATION_REJECTED("room.creation.rejected", Level.INFO, "Room creation rejected"),
    CREATION_FAILED("room.creation.failed", Level.ERROR, "Room creation failed"),
    LEFT("room.left", Level.INFO, "Room membership ended"),
    LEAVE_REJECTED("room.leave.rejected", Level.DEBUG, "Room leave rejected"),
    LEAVE_REPLAYED("room.leave.replayed", Level.DEBUG, "Room leave replayed"),
    DESTROYED("room.destroyed", Level.INFO, "Empty room destroyed"),
    START_REJECTED("room.start.rejected", Level.DEBUG, "Room start rejected"),
    REQUEST_CANCELLED("room.request.cancelled", Level.DEBUG, "Queued room request cancelled"),
    OPERATION_FAILED("room.operation.failed", Level.ERROR, "Room operation failed");

    private final String eventName;
    private final Level level;
    private final String message;

    RoomLogEvent(String eventName, Level level, String message) {
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
