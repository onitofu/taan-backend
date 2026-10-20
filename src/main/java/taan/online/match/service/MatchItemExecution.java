package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.energyAfter;
import static taan.online.logging.LogField.energyBefore;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.Match;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.service.model.ActivateSpecialCommand;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.PendingMatchSpend;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchEndReason;

final class MatchItemExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchItemExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final LongSupplier clock;
    private PendingMatchSpend pending;
    private boolean failed;

    MatchItemExecution(MatchRuntime owner, Match match, Object admission, LongSupplier clock) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.clock = clock;
    }

    static CompletionStage<Boolean> reportBonus(MatchRuntime runtime, ReportMatchBonusCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.reportBonus(command));
    }

    private boolean reportBonus(ReportMatchBonusCommand command) {
        var context = command.context();

        if (!reportCurrent(context)) {
            return false;
        }

        var participant = member(context);
        boolean valid = participant.slotIndex() == command.slotIndex() && command.slotIndex() == command.commandSlot()
            && (command.gaugeSlot().isEmpty() || command.gaugeSlot().orElseThrow() == command.slotIndex())
            && command.entityId() == -1L;
        LOG.log(
            MatchLogEvent.BONUS_IGNORED,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            admissionId(context.admissionId()),
            slotIndex(command.slotIndex()),
            reason(valid ? "BONUS_DISABLED" : "INVALID_BONUS_CONTEXT")
        );
        return valid;
    }

    static CompletionStage<Boolean> reportPickup(MatchRuntime runtime, ReportMatchPickupCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.reportPickup(command));
    }

    static CompletionStage<Boolean> confirmItemReceipt(MatchRuntime runtime, ConfirmMatchItemReceiptCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.confirmItemReceipt(command));
    }

    static CompletionStage<Boolean> discardItem(MatchRuntime runtime, DiscardMatchItemCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.discardItem(command));
    }

    boolean reportPickup(ReportMatchPickupCommand command) {
        var context = command.context();

        if (!reportCurrent(context)) {
            return false;
        }

        boolean valid = member(context).slotIndex() == command.slotIndex()
            && command.slotIndex() == command.commandSlot()
            && (command.gaugeSlot().isEmpty() || command.gaugeSlot().orElseThrow() == command.slotIndex())
            && command.entityId() != -1L;
        return ignored(context, command.slotIndex(), MatchLogEvent.PICKUP_IGNORED, valid, "UNSUPPORTED_PICKUP");
    }

    boolean confirmItemReceipt(ConfirmMatchItemReceiptCommand command) {
        return inventoryMessage(
            command.context(),
            command.slotIndex(),
            command.inventoryIndex(),
            MatchLogEvent.ITEM_RECEIPT_IGNORED
        );
    }

    boolean discardItem(DiscardMatchItemCommand command) {
        return inventoryMessage(
            command.context(),
            command.slotIndex(),
            command.inventoryIndex(),
            MatchLogEvent.ITEM_DISCARD_IGNORED
        );
    }

    private boolean inventoryMessage(MatchCommandContext context, int slot, int index, MatchLogEvent event) {
        if (!reportCurrent(context)) {
            return false;
        }

        boolean valid = member(context).slotIndex() == slot && index >= 0 && index < 3;
        return ignored(context, slot, event, valid, "FEATURE_DISABLED");
    }

    private boolean reportCurrent(MatchCommandContext context) {
        return owner.live() && owner.admits(context) && !match.resolution().exits().containsKey(context.sessionId())
            && (match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.SETTLING
                || match.phase() == MatchPhase.COMPLETION);
    }

    private boolean ignored(MatchCommandContext context, int slot, MatchLogEvent event, boolean valid, String reason) {
        LOG.log(
            event,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            admissionId(context.admissionId()),
            slotIndex(slot),
            reason(valid ? reason : "INVALID_ITEM_CONTEXT")
        );
        return valid;
    }

    boolean activate(ActivateSpecialCommand command) {
        if (!current(command.context(), command.turnRevision(), command.phaseRevision(), command.slot())) {
            return false;
        }

        var member = member(command.context());
        int before = match.actors().get(member.sessionId()).energy();
        boolean duplicate = match.attacks().get(member.sessionId()) == MatchAttackSelection.SPECIAL;

        if (command.itemId() != 6 || command.inventoryIndex() != 255 || command.flag12() != 0
            || command.flag13() != 0 || command.team() != team(member) || pending != null) {
            return reject("INVALID_SPECIAL_ACTIVATION");
        }

        if (duplicate) {
            LOG.log(
                MatchLogEvent.SPECIAL_DUPLICATE,
                matchId(match.id().value()),
                slotIndex(member.slotIndex()),
                sessionId(member.sessionId()),
                admissionId(member.admissionId()),
                channelId(match.channelId()),
                turnRevision(command.turnRevision())
            );
            return true;
        }

        boolean activated;

        synchronized (admission) {
            if (!owner.admits(command.context())) {
                return false;
            }

            activated = match.activateSpecial(member.sessionId());
            owner.refresh();
        }

        if (!activated) {
            return reject("INSUFFICIENT_SPECIAL_ENERGY");
        }

        LOG.log(
            MatchLogEvent.SPECIAL_ACTIVATED,
            matchId(match.id().value()),
            slotIndex(member.slotIndex()),
            sessionId(member.sessionId()),
            admissionId(member.admissionId()),
            channelId(match.channelId()),
            turnRevision(command.turnRevision()),
            energyBefore(before),
            energyAfter(0)
        );
        deliverSpecial(member);
        return true;
    }

    boolean record(RecordMatchSpendCommand command) {
        if (!current(command.context(), command.turnRevision(), command.phaseRevision(), command.slot())) {
            return false;
        }

        var member = member(command.context());
        int team = team(member);
        boolean special = match.attacks().get(member.sessionId()) == MatchAttackSelection.SPECIAL;
        var seen = new HashSet<Integer>();
        boolean valid = (command.teamClear11() == team || command.teamClear11() == 255)
            && (command.teamClear12() == team || command.teamClear12() == 255)
            && command.items().size() <= 3;

        for (var item : command.items()) {
            valid &= special && item.itemId() == 6 && item.scalar2() >= 0 && item.scalar2() <= 255
                && seen.add(item.itemId());
        }

        var value = new PendingMatchSpend(
            command.context(),
            command.turnRevision(),
            command.slot(),
            command.teamClear11(),
            command.teamClear12(),
            command.items()
        );

        if (!valid || pending != null && !pending.equals(value)) {
            return reject("CONFLICTING_SPEND");
        }

        synchronized (admission) {
            if (!owner.admits(command.context())) {
                return false;
            }

            pending = value;
        }

        LOG.log(
            MatchLogEvent.SPEND_RECORDED,
            matchId(match.id().value()),
            slotIndex(member.slotIndex()),
            sessionId(member.sessionId()),
            admissionId(member.admissionId()),
            channelId(match.channelId()),
            turnRevision(command.turnRevision())
        );
        return true;
    }

    private boolean current(MatchCommandContext context, long turn, long phase, int slot) {
        return owner.live() && owner.admits(context) && !match.resolution().exits().containsKey(context.sessionId())
            && match.phase() == MatchPhase.ACTION
            && match.turn().revision() == turn && match.phaseRevision() == phase && !match.expired(clock.getAsLong())
            && member(context).slotIndex() == slot && slot == match.turn().activeSlot()
            && !match.actors().get(context.sessionId()).dead();
    }

    private MatchParticipant member(MatchCommandContext context) {
        return match.participants().stream().filter(p -> p.sessionId().equals(context.sessionId()))
            .findFirst().orElseThrow();
    }

    private int team(MatchParticipant participant) {
        return participant.team() == RoomTeam.BLUE ? 1 : 2;
    }

    Optional<PendingMatchSpend> prepare() {
        if (match.attacks().get(match.participants().stream()
            .filter(p -> p.slotIndex() == match.turn().activeSlot()).findFirst().orElseThrow().sessionId())
            == MatchAttackSelection.SPECIAL && pending == null) {
            throw new ScenarioWorldException("MISSING_SPEND");
        }

        return Optional.ofNullable(pending);
    }

    void clear() {
        pending = null;
    }

    boolean canCloseTurn() {
        if (pending == null) {
            return true;
        }

        return reject("INCOMPLETE_SPEND_SEQUENCE");
    }

    private boolean reject(String reason) {
        LOG.log(MatchLogEvent.SPECIAL_REJECTED, matchId(match.id().value()), reason(reason));
        owner.terminate(RoomMatchEndReason.INVALID_SCENARIO);
        return false;
    }

    private void deliverSpecial(MatchParticipant actor) {
        try {
            for (var recipient : owner.recipients()) {
                if (recipientCurrent(recipient)) {
                    observe(recipient.output().special(actor.slotIndex(), team(actor)));
                }
            }
        } catch (RuntimeException failure) {
            deliveryFailed(failure);
        }
    }

    CompletionStage<Void> committed(Optional<PendingMatchSpend> spend) {
        if (spend.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var value = spend.orElseThrow();
        var deliveries = new ArrayList<CompletableFuture<Void>>();
        LOG.log(
            MatchLogEvent.SPEND_COMMITTED,
            matchId(match.id().value()),
            slotIndex(value.slot()),
            sessionId(value.context().sessionId()),
            admissionId(value.context().admissionId()),
            channelId(match.channelId()),
            turnRevision(value.turnRevision())
        );

        for (var recipient : owner.recipients()) {
            if (recipientCurrent(recipient)) {
                deliveries.add(recipient.output().spend(value).toCompletableFuture());
            }
        }

        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }

    private boolean recipientCurrent(MatchSubscription recipient) {
        var member = match.participants().stream().filter(p -> p.sessionId().equals(recipient.sessionId()))
            .findFirst().orElseThrow();
        return owner.admits(new MatchCommandContext(
            match.id(),
            match.roomId(),
            match.roomGeneration(),
            match.channelId(),
            member.sessionId(),
            member.admissionId()
        ));
    }

    private void observe(CompletionStage<Void> delivery) {
        delivery.handle(LogContext.propagate((ignored, failure) -> {
            if (failure != null) {
                owner.turnWork(() -> deliveryFailed(failure), () -> deliveryFailed(failure));
            }

            return null;
        }));
    }

    private boolean deliveryFailed(Throwable failure) {
        if (!owner.running() || failed) {
            return false;
        }

        failed = true;
        LOG.failure(MatchLogEvent.ITEM_DELIVERY_FAILED, failure, matchId(match.id().value()));
        owner.terminate(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED);
        return true;
    }
}
