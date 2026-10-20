package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.Match;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.ReportMatchBonusCommand;

final class MatchLootExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchLootExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final MatchLootPolicy policy;

    MatchLootExecution(MatchRuntime owner, Match match, Object admission, MatchLootPolicy policy) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.policy = policy;
    }

    void offerBonus() {
        synchronized (admission) {
            if (!policy.enabled() || !owner.live() || !owner.running() || !match.mode().usesItems()) {
                return;
            }

            var member = match.participants().stream().filter(p -> p.slotIndex() == match.turn().activeSlot())
                .findFirst().orElseThrow();
            var context = MatchCommandContext.from(owner.snapshot(), member.sessionId());

            if (!current(context) || match.actors().get(member.sessionId()).dead()) {
                return;
            }

            var previous = match.loot().offers().get(member.sessionId());

            if (previous != null) {
                return;
            }

            var offer = new MatchBonusOffer(
                member.sessionId(),
                member.admissionId(),
                match.turn().revision(),
                Math.toIntExact(match.turn().pickerBase()),
                false
            );
            match.updateLoot(match.loot().offered(offer));
            owner.refresh();
        }
    }

    boolean reportBonus(ReportMatchBonusCommand command) {
        var context = command.context();

        synchronized (admission) {
            if (!current(context)) {
                return false;
            }

            boolean valid = member(context).slotIndex() == command.slotIndex()
                && command.slotIndex() == command.commandSlot()
                && (command.gaugeSlot().isEmpty() || command.gaugeSlot().orElseThrow() == command.slotIndex())
                && command.entityId() == -1L;

            if (!valid || !policy.enabled()) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.BONUS_IGNORED,
                    valid ? "BONUS_DISABLED" : "INVALID_BONUS_CONTEXT",
                    valid
                );
            }

            var offer = match.loot().offers().get(context.sessionId());

            if (offer == null || offer.consumed() || !offer.admissionId().equals(context.admissionId())
                || match.actors().get(context.sessionId()).dead() || command.gaugeSlot().isPresent()
                || !policy.permits(command.itemId(), offer.pickerScalar())) {
                return ignored(context, command.slotIndex(), MatchLogEvent.BONUS_IGNORED, "INVALID_BONUS_OFFER", false);
            }

            boolean inserted = match.loot().inventory(context.sessionId()).firstEmpty() >= 0;
            match.updateLoot(match.loot().granted(context.sessionId(), command.itemId()));
            owner.refresh();
            LOG.log(
                inserted ? MatchLogEvent.BONUS_GRANTED : MatchLogEvent.BONUS_IGNORED,
                matchId(match.id().value()),
                sessionId(context.sessionId()),
                admissionId(context.admissionId()),
                slotIndex(command.slotIndex()),
                turnRevision(offer.turnRevision()),
                reason(inserted ? "INSERTED" : "INVENTORY_FULL")
            );
        }

        var bonus = new MatchBonusDelivery(
            command.slotIndex(),
            command.tick(),
            command.scalar10(),
            command.itemId(),
            command.opaque24()
        );
        deliver(output -> output.bonus(bonus));
        return true;
    }

    boolean confirm(ConfirmMatchItemReceiptCommand command) {
        var context = command.context();

        synchronized (admission) {
            if (!inventoryMessage(context, command.slotIndex(), command.inventoryIndex(), false)) {
                return false;
            }

            if (!policy.enabled()) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.ITEM_RECEIPT_IGNORED,
                    "FEATURE_DISABLED",
                    true
                );
            }

            var next = match.loot().confirmed(context.sessionId(), command.inventoryIndex(), command.itemId());

            if (next == match.loot()) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.ITEM_RECEIPT_IGNORED,
                    "UNEXPECTED_RECEIPT",
                    false
                );
            }

            match.updateLoot(next);
            owner.refresh();
            accepted(context, command.slotIndex(), MatchLogEvent.ITEM_RECEIPT_CONFIRMED);
            return true;
        }
    }

    boolean discard(DiscardMatchItemCommand command) {
        var context = command.context();

        synchronized (admission) {
            if (!inventoryMessage(context, command.slotIndex(), command.inventoryIndex(), true)) {
                return false;
            }

            if (!policy.enabled()) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.ITEM_DISCARD_IGNORED,
                    "FEATURE_DISABLED",
                    true
                );
            }

            if (match.actors().get(context.sessionId()).dead()) {
                return ignored(context, command.slotIndex(), MatchLogEvent.ITEM_DISCARD_IGNORED, "DEAD_ACTOR", false);
            }

            var next = match.loot().discarded(context.sessionId(), command.inventoryIndex(), command.itemId());

            if (next == match.loot()) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.ITEM_DISCARD_IGNORED,
                    "UNCONFIRMED_ITEM",
                    false
                );
            }

            match.updateLoot(next);
            owner.refresh();
            accepted(context, command.slotIndex(), MatchLogEvent.ITEM_DISCARDED);
        }

        deliver(output -> output.discard(command));
        return true;
    }

    private boolean current(MatchCommandContext context) {
        return owner.live() && owner.admits(context) && !match.resolution().exits().containsKey(context.sessionId())
            && (match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.SETTLING
                || match.phase() == MatchPhase.COMPLETION);
    }

    private MatchParticipant member(MatchCommandContext context) {
        return match.participants().stream().filter(p -> p.sessionId().equals(context.sessionId()))
            .findFirst().orElseThrow();
    }

    private boolean inventoryMessage(MatchCommandContext context, int slot, int index, boolean discard) {
        if (!current(context)) {
            return false;
        }

        if (member(context).slotIndex() != slot || index < 0 || index >= BattleInventory.CAPACITY) {
            return ignored(
                context,
                slot,
                discard ? MatchLogEvent.ITEM_DISCARD_IGNORED : MatchLogEvent.ITEM_RECEIPT_IGNORED,
                "INVALID_ITEM_CONTEXT",
                false
            );
        }

        return true;
    }

    private void accepted(MatchCommandContext context, int slot, MatchLogEvent event) {
        LOG.log(
            event,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            admissionId(context.admissionId()),
            slotIndex(slot)
        );
    }

    private boolean ignored(MatchCommandContext context, int slot, MatchLogEvent event, String cause, boolean valid) {
        LOG.log(
            event,
            matchId(match.id().value()),
            sessionId(context.sessionId()),
            admissionId(context.admissionId()),
            slotIndex(slot),
            reason(cause)
        );
        return valid;
    }

    private void deliver(Function<MatchOutput, CompletionStage<Void>> delivery) {
        try {
            for (var recipient : owner.recipients()) {
                if (owner.items.recipientCurrent(recipient)) {
                    owner.items.observe(delivery.apply(recipient.output()));
                }
            }
        } catch (RuntimeException failure) {
            owner.items.deliveryFailed(failure);
        }
    }
}
