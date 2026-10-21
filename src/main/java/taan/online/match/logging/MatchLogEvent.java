package taan.online.match.logging;

import org.slf4j.event.Level;
import taan.online.logging.LogEvent;

public enum MatchLogEvent implements LogEvent {
    NUCLEAR_ACTIVATED("match.nuclear.activated", Level.DEBUG, "Nuclear item activated"),
    NUCLEAR_DUPLICATE("match.nuclear.duplicate", Level.DEBUG, "Duplicate nuclear activation ignored"),
    NUCLEAR_REJECTED("match.nuclear.rejected", Level.DEBUG, "Nuclear activation rejected"),
    GHOST_REQUEST_IGNORED("match.ghost.request.ignored", Level.DEBUG, "Ghost request ignored"),
    GUIDED_ACTIVATED("match.guided.activated", Level.DEBUG, "Guided item activated"),
    GUIDED_DUPLICATE("match.guided.duplicate", Level.DEBUG, "Duplicate guided activation ignored"),
    GUIDED_REJECTED("match.guided.rejected", Level.DEBUG, "Guided activation rejected"),
    SPREAD_ACTIVATED("match.spread.activated", Level.DEBUG, "Projectile spread item activated"),
    SPREAD_DUPLICATE("match.spread.duplicate", Level.DEBUG, "Duplicate projectile spread activation ignored"),
    SPREAD_REJECTED("match.spread.rejected", Level.DEBUG, "Projectile spread activation rejected"),
    DAMAGE_UP_ACTIVATED("match.damageup.activated", Level.DEBUG, "Damage amplification item activated"),
    DAMAGE_UP_DUPLICATE("match.damageup.duplicate", Level.DEBUG, "Duplicate damage amplification activation ignored"),
    DAMAGE_UP_REJECTED("match.damageup.rejected", Level.DEBUG, "Damage amplification activation rejected"),
    REPEAT_ACTIVATED("match.repeat.activated", Level.DEBUG, "Additional shot item activated"),
    REPEAT_DUPLICATE("match.repeat.duplicate", Level.DEBUG, "Duplicate additional shot activation ignored"),
    REPEAT_REJECTED("match.repeat.rejected", Level.DEBUG, "Additional shot activation rejected"),
    POWER_ACTIVATED("match.power.activated", Level.DEBUG, "Power item activated"),
    POWER_DUPLICATE("match.power.duplicate", Level.DEBUG, "Duplicate power activation ignored"),
    POWER_REJECTED("match.power.rejected", Level.DEBUG, "Power activation rejected"),
    BOX_PICKED_UP("match.box.picked_up", Level.DEBUG, "Match item box picked up"),
    BOX_CREATED("match.box.created", Level.DEBUG, "Match item box created"),
    BONUS_GRANTED("match.bonus.granted", Level.DEBUG, "Match bonus grant authorized"),
    ITEM_RECEIPT_CONFIRMED("match.item.receipt.confirmed", Level.DEBUG, "Match item receipt confirmed"),
    ITEM_DISCARDED("match.item.discarded", Level.DEBUG, "Match item discarded"),
    PICKUP_IGNORED("match.pickup.ignored", Level.DEBUG, "Match pickup report ignored"),
    ITEM_RECEIPT_IGNORED("match.item.receipt.ignored", Level.DEBUG, "Match item receipt ignored"),
    ITEM_DISCARD_IGNORED("match.item.discard.ignored", Level.DEBUG, "Match item discard ignored"),
    BONUS_IGNORED("match.bonus.ignored", Level.DEBUG, "Match bonus report ignored"),
    DEPARTURE_ACCEPTED("match.departure.accepted", Level.DEBUG, "Match departure accepted"),
    FINISHED("match.finished", Level.INFO, "Match finished without awards"),
    OUTCOME_DECIDED("match.outcome.decided", Level.DEBUG, "Match outcome decided"),
    ITEM_DELIVERY_FAILED("match.item.delivery.failed", Level.ERROR, "Match item delivery failed"),
    SPEND_COMMITTED("match.spend.committed", Level.DEBUG, "Item spending committed"),
    SPEND_RECORDED("match.spend.recorded", Level.DEBUG, "Item spending staged"),
    SPECIAL_REJECTED("match.special.rejected", Level.DEBUG, "Special item sequence rejected"),
    SPECIAL_DUPLICATE("match.special.duplicate", Level.DEBUG, "Duplicate special activation ignored"),
    SPECIAL_ACTIVATED("match.special.activated", Level.DEBUG, "Special ability activated"),
    ENERGY_TURN_GRANTED("match.energy.turn.granted", Level.DEBUG, "Turn energy granted"),
    ENERGY_SCENARIO_APPLIED("match.energy.scenario.applied", Level.DEBUG, "Scenario energy applied"),
    MOVEMENT_ACCEPTED("match.movement.accepted", Level.DEBUG, "Movement anchor accepted"),
    MOVEMENT_REJECTED("match.movement.rejected", Level.DEBUG, "Movement anchor rejected"),
    MOVEMENT_IGNORED("match.movement.ignored", Level.DEBUG, "Stale or inactive movement ignored"),
    MOVEMENT_SETTLED("match.movement.settled", Level.DEBUG, "Movement settlement completed"),
    TURN_PASSED("match.turn.passed", Level.DEBUG, "Match turn passed"),
    TURN_STOPPING("match.turn.stopping", Level.DEBUG, "Match action watchdog stopped the turn"),
    MOVEMENT_DELIVERY_FAILED("match.movement.delivery.failed", Level.ERROR, "Movement delivery failed"),
    SCENARIO_ACKNOWLEDGED("match.scenario.acknowledged", Level.DEBUG, "Scenario acknowledgement accepted"),
    SCENARIO_ACK_IGNORED("match.scenario.ack.ignored", Level.DEBUG, "Stale or duplicate acknowledgement ignored"),
    SCENARIO_COMPLETED("match.scenario.completed", Level.DEBUG, "Scenario completion barrier passed"),
    TURN_ISSUED("match.turn.issued", Level.DEBUG, "Next match turn issued"),
    COMPLETION_TIMEOUT("match.scenario.completion.timeout", Level.WARN, "Scenario acknowledgement deadline expired"),
    TURN_TIMEOUT("match.turn.timeout", Level.DEBUG, "Match action deadline forced a pass"),
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
