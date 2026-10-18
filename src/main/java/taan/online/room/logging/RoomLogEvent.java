package taan.online.room.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum RoomLogEvent implements LogEvent {
    READY_SET("room.ready.set", Level.DEBUG, "Room readiness accepted"),
    READY_REJECTED("room.ready.rejected", Level.DEBUG, "Room readiness rejected"),
    READY_RESET("room.ready.reset", Level.DEBUG, "Room readiness reset"),
    READY_FAILED("room.ready.failed", Level.ERROR, "Room readiness failed"),
    TEAM_CHANGED("room.team.changed", Level.DEBUG, "Room team changed"),
    TEAM_UNCHANGED("room.team.unchanged", Level.DEBUG, "Room team unchanged"),
    TEAM_CHANGE_REJECTED("room.team.change.rejected", Level.DEBUG, "Room team change rejected"),
    TEAM_CHANGE_FAILED("room.team.change.failed", Level.ERROR, "Room team change failed"),
    SLOT_OPENED("room.slot.opened", Level.DEBUG, "Room slot opening accepted"),
    SLOT_CLOSED("room.slot.closed", Level.DEBUG, "Room slot closing accepted"),
    SLOT_AVAILABILITY_REJECTED("room.slot.availability.rejected", Level.DEBUG, "Room slot availability rejected"),
    SLOT_AVAILABILITY_FAILED("room.slot.availability.failed", Level.ERROR, "Room slot availability failed"),
    QUICK_JOIN_REJECTED("room.quick_join.rejected", Level.DEBUG, "Quick join rejected"),
    QUICK_JOIN_FAILED("room.quick_join.failed", Level.ERROR, "Quick join failed"),
    ENTRY_SUCCEEDED("room.entry.succeeded", Level.INFO, "Room entered"),
    ENTRY_FAILED("room.entry.failed", Level.ERROR, "Room entry failed"),
    OWNER_CHANGED("room.owner.changed", Level.INFO, "Room owner changed"),
    NOTIFICATION_FAILED("room.notification.failed", Level.ERROR, "Room notification failed"),
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
