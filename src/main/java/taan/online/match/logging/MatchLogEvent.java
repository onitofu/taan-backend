package taan.online.match.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum MatchLogEvent implements LogEvent {
    SCENARIO_ACKNOWLEDGED("match.scenario.acknowledged", Level.DEBUG, "Scenario acknowledgement accepted"),
    SCENARIO_ACK_IGNORED("match.scenario.ack.ignored", Level.DEBUG, "Stale or duplicate acknowledgement ignored"),
    SCENARIO_COMPLETED("match.scenario.completed", Level.DEBUG, "Scenario completion barrier passed"),
    TURN_ISSUED("match.turn.issued", Level.DEBUG, "Next match turn issued"),
    COMPLETION_TIMEOUT("match.scenario.completion.timeout", Level.WARN, "Scenario acknowledgement deadline expired"),
    TURN_TIMEOUT("match.turn.timeout", Level.WARN, "Match action deadline expired"),
    TURN_DELIVERY_FAILED("match.turn.delivery.failed", Level.ERROR, "Match turn delivery failed"),
    SCENARIO_APPLIED("match.scenario.applied", Level.DEBUG, "Scenario applied to match world"),
    SCENARIO_APPLY_FAILED("match.scenario.apply.failed", Level.ERROR, "Scenario application failed"),
    SCENARIO_DELIVERY_FAILED("match.scenario.delivery.failed", Level.ERROR, "Committed scenario delivery failed"),
    SCENARIO_VALIDATED("match.scenario.validated", Level.DEBUG, "Scenario validated without applying game state"),
    SCENARIO_REJECTED("match.scenario.rejected", Level.DEBUG, "Scenario rejected"),
    SCENARIO_DUPLICATE("match.scenario.duplicate", Level.DEBUG, "Duplicate scenario ignored"),
    SCENARIO_IGNORED("match.scenario.ignored", Level.DEBUG, "Stale or inactive scenario ignored"),
    FIRST_TURN_ISSUED("match.first_turn.issued", Level.DEBUG, "First match turn issued"),
    COMMAND_PROCESSED("match.command.processed", Level.TRACE, "Match command processed"),
    COMMAND_REJECTED("match.command.rejected", Level.DEBUG, "Match command rejected"),
    COMMAND_FAILED("match.command.failed", Level.ERROR, "Match command failed"),
    LOADING_ACK("match.loading.ack", Level.DEBUG, "Match loading acknowledgement accepted"),
    POSITION_CONFIRMED("match.position.confirmed", Level.DEBUG, "Match position confirmed"),
    STARTED("match.started", Level.INFO, "Match first turn started"),
    BOOTSTRAP_DELIVERY_FAILED("match.bootstrap.delivery.failed", Level.ERROR, "Match bootstrap delivery failed"),
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
