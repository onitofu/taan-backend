package taan.online.match.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum MatchLogEvent implements LogEvent {
    RESERVED("match.reserved", Level.DEBUG, "Match reserved"),
    LOADING("match.loading", Level.DEBUG, "Match loading committed"),
    RESERVATION_REJECTED("match.reservation.rejected", Level.DEBUG, "Match reservation rejected"),
    RESERVATION_ROLLED_BACK("match.reservation.rolled_back", Level.DEBUG, "Match reservation rolled back"),
    CANCELLED("match.cancelled", Level.INFO, "Match cancelled"),
    TERMINATION_IGNORED("match.termination.ignored", Level.DEBUG, "Stale match termination ignored"),
    RESULT_SELECTION_IGNORED("match.result.selection.ignored", Level.DEBUG, "Preview card selection ignored"),
    TERMINAL_DELIVERY_FAILED("match.terminal.delivery.failed", Level.ERROR, "Match terminal delivery failed"),
    OPERATION_FAILED("match.operation.failed", Level.ERROR, "Match operation failed"),
    REQUEST_CANCELLED("match.request.cancelled", Level.DEBUG, "Queued match request cancelled"),
    RESERVATION_FAILED("match.reservation.failed", Level.ERROR, "Match reservation failed");

    private final String eventName;
    private final Level level;
    private final String message;

    MatchLogEvent(String eventName, Level level, String message) {
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
