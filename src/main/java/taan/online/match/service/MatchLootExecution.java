package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.inventoryIndex;
import static taan.online.logging.LogField.itemId;
import static taan.online.logging.LogField.lootPickerScalar;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.scenarioHash;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.Optional;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.Match;
import taan.online.match.model.MatchBonusOffer;
import taan.online.match.model.MatchItemSource;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.PowerQuizOpportunity;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.room.model.RoomRule;

final class MatchLootExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchLootExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final MatchLootPolicy policy;
    private final LongSupplier clock;

    MatchLootExecution(
        MatchRuntime owner,
        Match match,
        Object admission,
        MatchLootPolicy policy,
        LongSupplier clock
    ) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.policy = policy;
        this.clock = clock;
    }

    void offerBonus() {
        synchronized (admission) {
            if (!policy.enabled() || !owner.live() || !owner.running() || match.rule() != RoomRule.BONUS) {
                return;
            }

            var member = match.participants().stream().filter(p -> p.slotIndex() == match.turn().activeSlot())
                .findFirst().orElseThrow();
            var context = MatchCommandContext.from(owner.snapshot(), member.sessionId());

            if (!owner.reportCurrent(context) || match.actors().get(member.sessionId()).dead()) {
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
        if (match.rule() == RoomRule.POWER_QUIZ) {
            return reportQuiz(command);
        }

        var context = command.context();

        synchronized (admission) {
            if (!owner.reportCurrent(context)) {
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

            if (match.actors().get(context.sessionId()).dead() || offer == null || offer.consumed()
                || !offer.admissionId().equals(context.admissionId()) || command.gaugeSlot().isPresent()
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
                lootPickerScalar(offer.pickerScalar()),
                reason(inserted ? "INSERTED" : "INVENTORY_FULL")
            );
        }

        deliverBonus(command);
        return true;
    }

    private boolean reportQuiz(ReportMatchBonusCommand command) {
        var context = command.context();
        boolean inserted;

        synchronized (admission) {
            if (!owner.reportCurrent(context)) {
                return false;
            }

            var opportunity = match.loot().quizOpportunities().get(command.slotIndex());
            boolean valid = member(context).slotIndex() == command.slotIndex()
                && command.commandSlot() == command.slotIndex() && command.entityId() == -1L
                && command.gaugeSlot().isEmpty();

            if (!valid || opportunity == null || opportunity.consumed()
                || !opportunity.admissionId().equals(context.admissionId()) || !quizWindow(opportunity)
                || !policy.permits(command.itemId(), opportunity.pickerScalar())) {
                return ignored(
                    context,
                    command.slotIndex(),
                    MatchLogEvent.POWER_QUIZ_IGNORED,
                    "INVALID_QUIZ_CLAIM",
                    false
                );
            }

            int index = match.loot().inventory(context.sessionId()).firstEmpty();
            inserted = index >= 0;
            match.updateLoot(match.loot().grantQuiz(command.slotIndex(), command.itemId()));
            owner.refresh();
            LOG.log(
                MatchLogEvent.POWER_QUIZ_ACCEPTED,
                matchId(match.id().value()),
                sessionId(context.sessionId()),
                admissionId(context.admissionId()),
                slotIndex(command.slotIndex()),
                turnRevision(opportunity.turnRevision()),
                scenarioHash(opportunity.scenarioHash()),
                lootPickerScalar(opportunity.pickerScalar()),
                itemId(command.itemId()),
                inventoryIndex(index),
                reason(inserted ? "INSERTED" : "INVENTORY_FULL")
            );
        }

        if (inserted) {
            deliverBonus(command);
        }

        return true;
    }

    private boolean quizWindow(PowerQuizOpportunity opportunity) {
        return match.phase() == MatchPhase.COMPLETION && !match.expired(clock.getAsLong())
            && match.completion().orElseThrow().fingerprint().equals(opportunity.scenarioHash())
            && match.turn().revision() == opportunity.turnRevision();
    }

    private void deliverBonus(ReportMatchBonusCommand command) {
        var bonus = new MatchBonusDelivery(
            command.slotIndex(),
            command.tick(),
            command.scalar10(),
            command.itemId(),
            command.opaque24()
        );
        owner.items.deliver(output -> output.bonus(bonus));
    }

    static void logQuizChanges(MatchSnapshot before, MatchSnapshot after) {
        var previous = before.loot().quizOpportunities();
        var current = after.loot().quizOpportunities();

        for (var opportunity : previous.values()) {
            if (!current.containsKey(opportunity.slotIndex())) {
                logQuizBoundary(MatchLogEvent.POWER_QUIZ_CLOSED, after, opportunity);
            }
        }

        for (var opportunity : current.values()) {
            if (!previous.containsKey(opportunity.slotIndex())) {
                logQuizBoundary(MatchLogEvent.POWER_QUIZ_OPENED, after, opportunity);
            }
        }
    }

    private static void logQuizBoundary(MatchLogEvent event, MatchSnapshot snapshot, PowerQuizOpportunity opportunity) {
        LOG.log(
            event,
            matchId(snapshot.id().value()),
            sessionId(opportunity.sessionId()),
            admissionId(opportunity.admissionId()),
            slotIndex(opportunity.slotIndex()),
            turnRevision(opportunity.turnRevision()),
            scenarioHash(opportunity.scenarioHash())
        );
    }

    private boolean quizReceipt(ConfirmMatchItemReceiptCommand command) {
        var opportunity = match.loot().quizOpportunities().get(command.slotIndex());
        return opportunity != null && quizWindow(opportunity)
            && match.loot().inventory(command.context().sessionId()).receipts().stream().anyMatch(
                receipt -> receipt.inventoryIndex() == command.inventoryIndex()
                    && receipt.source() == MatchItemSource.POWER_QUIZ
                    && receipt.scenarioHash().equals(Optional.of(opportunity.scenarioHash()))
            );
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

            if (match.actors().get(context.sessionId()).dead() && !quizReceipt(command)) {
                return ignored(context, command.slotIndex(), MatchLogEvent.ITEM_RECEIPT_IGNORED, "DEAD_ACTOR", true);
            }

            var next = match.loot().confirmed(
                context.sessionId(),
                context.admissionId(),
                command.inventoryIndex(),
                command.itemId()
            );

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

        owner.items.deliver(output -> output.discard(command));
        return true;
    }

    private MatchParticipant member(MatchCommandContext context) {
        return match.participants().stream().filter(p -> p.sessionId().equals(context.sessionId()))
            .findFirst().orElseThrow();
    }

    private boolean inventoryMessage(MatchCommandContext context, int slot, int index, boolean discard) {
        if (!owner.reportCurrent(context)) {
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

}
