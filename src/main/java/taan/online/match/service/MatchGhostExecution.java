package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.boxEntityId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.itemId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;
import static taan.online.logging.LogField.worldRevision;

import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalLong;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.GhostPoint;
import taan.online.match.model.Match;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.TeamGaugeRules;
import taan.online.match.model.TeamGaugeState;
import taan.online.match.service.model.ConfirmMatchDropCommand;
import taan.online.match.service.model.ConfirmMatchGhostPositionCommand;
import taan.online.match.service.model.MakeMatchGhostCommand;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchGhostNotice;
import taan.online.match.service.model.MatchGhostNoticeKind;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.ScenarioTeamGaugeClaim;
import taan.online.match.service.model.SetMatchGhostTargetCommand;

final class MatchGhostExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchGhostExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;

    MatchGhostExecution(MatchRuntime owner, Match match, Object admission) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
    }

    boolean drop(ConfirmMatchDropCommand command, long now) {
        synchronized (admission) {
            if (!current(command.context(), command.slotIndex())
                || !match.confirmDrop(command.context().sessionId(), command.x(), command.y(), now)) {
                return false;
            }

            owner.items.clear();
            owner.refresh();
        }

        log(MatchLogEvent.DROP_CONFIRMED, command.context(), command.slotIndex(), "TRACKED_FALL");
        var notice = new MatchGhostNotice(
            MatchGhostNoticeKind.DROP,
            command.slotIndex(),
            command.x(),
            command.y(),
            0
        );
        owner.items.deliver(command.context().sessionId(), output -> output.ghost(notice));
        owner.lifecycle.advance(true);
        return true;
    }

    boolean make(MakeMatchGhostCommand command) {
        MatchGhostNotice notice;

        synchronized (admission) {
            if (!current(command.context(), command.slotIndex())) {
                return false;
            }

            if (match.ghosts().containsKey(command.context().sessionId())) {
                return true;
            }

            if (!match.makeGhost(command.context().sessionId(), command.slotIndex())) {
                return false;
            }

            var ghost = match.ghosts().get(command.context().sessionId());
            notice = new MatchGhostNotice(
                MatchGhostNoticeKind.CREATED,
                ghost.slotIndex(),
                ghost.position().x(),
                ghost.position().y(),
                ghost.facing()
            );
            owner.refresh();
        }

        log(MatchLogEvent.GHOST_CREATED, command.context(), command.slotIndex(), "CREATED");
        owner.items.deliver(output -> output.ghost(notice));
        return true;
    }

    boolean target(SetMatchGhostTargetCommand command, long now) {
        MatchGhostNotice notice;

        synchronized (admission) {
            if (!current(command.context(), command.slotIndex())) {
                return false;
            }

            if (!match.setGhostTarget(command.context().sessionId(), new GhostPoint(command.x(), command.y()))) {
                log(MatchLogEvent.GHOST_REQUEST_IGNORED, command.context(), command.slotIndex(), "INVALID_TARGET");
                return false;
            }

            match.advanceGhosts(now);
            notice = new MatchGhostNotice(
                MatchGhostNoticeKind.TARGET,
                command.slotIndex(),
                command.x(),
                command.y(),
                0
            );
            owner.refresh();
        }

        log(MatchLogEvent.GHOST_TARGET_UPDATED, command.context(), command.slotIndex(), "TARGET");
        owner.items.deliver(output -> output.ghost(notice));
        return true;
    }

    boolean position(ConfirmMatchGhostPositionCommand command, long now) {
        MatchGhostNotice correction;

        synchronized (admission) {
            if (!current(command.context(), command.slotIndex())) {
                return false;
            }

            if (!match.ghosts().containsKey(command.context().sessionId())) {
                return false;
            }

            match.advanceGhosts(now);
            var ghost = match.ghosts().get(command.context().sessionId());
            owner.refresh();

            if (ghost.segment().isPresent() || ghost.position().x() == command.x()
                && ghost.position().y() == command.y() && ghost.facing() == command.facing()) {
                return true;
            }

            correction = new MatchGhostNotice(
                MatchGhostNoticeKind.POSITION,
                ghost.slotIndex(),
                ghost.position().x(),
                ghost.position().y(),
                ghost.facing()
            );
        }

        log(MatchLogEvent.GHOST_POSITION_CORRECTED, command.context(), command.slotIndex(), "POSITION_MISMATCH");
        owner.items.deliver(output -> output.ghost(correction));
        return true;
    }

    boolean pickup(ReportMatchPickupCommand command, long now, MatchLootPolicy policy) {
        Optional<ScenarioTeamGaugeClaim> canonical = command.gauge();

        synchronized (admission) {
            if (!current(command.context(), command.slotIndex()) || command.slotIndex() != command.commandSlot()
                || !match.ghosts().containsKey(command.context().sessionId()) || match.ghostPickupWindow().isEmpty()) {
                return rejectedPickup(command, "INVALID_GHOST_CONTEXT");
            }

            var window = match.ghostPickupWindow().orElseThrow();
            var box = match.world().boxes().get(command.entityId());
            long expectedTurn = command.turnRevision() < 0 ? match.turn().revision() : command.turnRevision();

            if (window.turnRevision() != expectedTurn || box == null || !box.active()) {
                return rejectedPickup(command, "UNAUTHORIZED_GHOST_PICKUP");
            }

            var authorization = window.authorize(command.slotIndex(), command.entityId(), now / 1000000);

            if (authorization.isEmpty()) {
                return rejectedPickup(command, "UNAUTHORIZED_GHOST_PICKUP");
            }

            var participant = match.participants().stream()
                .filter(member -> member.sessionId().equals(command.context().sessionId())).findFirst().orElseThrow();
            var world = match.world();
            var inventory = match.loot();

            if (match.mode().usesItems()) {
                if (command.gauge().isPresent() || !policy.permits(command.itemId(), 100)) {
                    return rejectedPickup(command, "INVALID_GHOST_REWARD");
                }

                inventory = inventory.inserted(
                    participant.sessionId(),
                    participant.admissionId(),
                    match.turn().revision(),
                    command.itemId(),
                    MatchItemSource.GHOST_PICKUP,
                    OptionalLong.of(command.entityId()),
                    Optional.empty()
                );
            } else {
                var state = world.teamGauges().get(participant.team());
                var claim = command.gauge();
                int count = match.participants().size();
                boolean valid = command.itemId() == 0 && policy.enabled();

                if (claim.isEmpty()) {
                    valid &= TeamGaugeRules.level(count, state.committed()) >= 4;
                } else {
                    var reported = claim.orElseThrow();
                    valid &= reported.header().kind() == 12 && reported.header().slot() == command.slotIndex()
                        && reported.reason() == 3
                        && window.observedGauges().get(participant.team()).contains(reported.oldGauge())
                        && TeamGaugeRules.level(count, reported.oldGauge()) < 4
                        && reported.newGauge() == reported.oldGauge() + 5;
                }

                if (!valid) {
                    return rejectedPickup(command, "INVALID_GHOST_GAUGE");
                }

                if (claim.isPresent()) {
                    var reported = claim.orElseThrow();
                    var delta = TeamGaugeRules.generate(
                        new TeamGaugeState(reported.oldGauge(), state.pending()),
                        count,
                        3,
                        5
                    ).orElseThrow();
                    canonical = Optional.of(new ScenarioTeamGaugeClaim(
                        reported.header(),
                        reported.scalar10(),
                        delta.reason(),
                        delta.newGauge(),
                        delta.oldGauge()
                    ));
                    var gauges = new HashMap<>(world.teamGauges());
                    gauges.put(participant.team(), TeamGaugeRules.replay(delta));
                    world = world.gauges(gauges);
                    authorization = authorization.map(value -> value.observed(participant.team(), delta.newGauge()));
                }
            }

            var updated = world.pickedBox(command.entityId(), command.itemId());
            match.commitGhostPickup(updated, inventory, authorization.orElseThrow());
            match.advanceGhosts(now);
            owner.refresh();
        }

        logPickup(MatchLogEvent.GHOST_PICKUP_ACCEPTED, command, "PICKED_UP");
        var delivery = new MatchBonusDelivery(
            command.slotIndex(),
            command.tick(),
            command.scalar10(),
            command.itemId(),
            command.opaque24(),
            canonical,
            command.entityId()
        );
        owner.items.deliver(output -> output.bonus(delivery));
        return true;
    }

    private boolean rejectedPickup(ReportMatchPickupCommand command, String cause) {
        logPickup(MatchLogEvent.GHOST_PICKUP_REJECTED, command, cause);
        return false;
    }

    private void logPickup(MatchLogEvent event, ReportMatchPickupCommand command, String cause) {
        LOG.log(
            event,
            matchId(match.id().value()),
            sessionId(command.context().sessionId()),
            admissionId(command.context().admissionId()),
            channelId(match.channelId()),
            slotIndex(command.slotIndex()),
            turnRevision(match.turn().revision()),
            worldRevision(match.world().revision()),
            boxEntityId(command.entityId()),
            itemId(command.itemId()),
            reason(cause)
        );
    }

    private boolean current(MatchCommandContext context, int slot) {
        return owner.reportCurrent(context) && match.resolution().pendingOutcome().isEmpty()
            && match.participants().stream().anyMatch(member -> member.sessionId().equals(context.sessionId())
                && member.slotIndex() == slot);
    }

    private void log(MatchLogEvent event, MatchCommandContext context, int slot, String cause) {
        LOG.log(
            event,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            admissionId(context.admissionId()),
            slotIndex(slot),
            turnRevision(match.turn().revision()),
            reason(cause)
        );
    }
}
