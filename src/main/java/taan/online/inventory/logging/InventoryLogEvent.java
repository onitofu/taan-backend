package taan.online.inventory.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum InventoryLogEvent implements LogEvent {
    MOVE_SUCCEEDED("inventory.move.succeeded", Level.INFO, "Inventory item moved"),
    MOVE_REJECTED("inventory.move.rejected", Level.DEBUG, "Inventory move rejected"),
    MOVE_FAILED("inventory.move.failed", Level.ERROR, "Inventory operation failed"),
    REQUEST_CANCELLED("inventory.request.cancelled", Level.DEBUG, "Inventory request cancelled");

    private final String event;
    private final Level level;
    private final String message;

    InventoryLogEvent(String event, Level level, String message) {
        this.event = event;
        this.level = level;
        this.message = message;
    }

    @Override
    public String eventName() {
        return event;
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
