package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.energyAfter;
import static taan.online.logging.LogField.energyBefore;
import static taan.online.logging.LogField.inventoryIndex;
import static taan.online.logging.LogField.itemId;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.LongSupplier;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.BattleInventory;
import taan.online.match.model.Match;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.service.model.ConfirmMatchItemReceiptCommand;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.PendingMatchSpend;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.ReportMatchBonusCommand;
import taan.online.match.service.model.ReportMatchPickupCommand;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.RoomMatchEndReason;

final class MatchItemExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchItemExecution.class);
    private final MatchRuntime owner;
    private final Match match;
    private final Object admission;
    private final LongSupplier clock;
    private final MatchLootPolicy policy;
    private final WarningLogLimiter warnings;
    final MatchLootExecution loot;
    private PendingMatchSpend pending;
    private boolean failed;

    MatchItemExecution(
        MatchRuntime owner,
        Match match,
        Object admission,
        LongSupplier clock,
        MatchLootPolicy policy,
        WarningLogLimiter warnings
    ) {
        this.owner = owner;
        this.match = match;
        this.admission = admission;
        this.clock = clock;
        this.policy = policy;
        this.warnings = warnings;
        loot = new MatchLootExecution(owner, match, admission, policy, clock);
    }

    static CompletionStage<Boolean> reportBonus(MatchRuntime runtime, ReportMatchBonusCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.loot.reportBonus(command));
    }

    static CompletionStage<Boolean> reportPickup(MatchRuntime runtime, ReportMatchPickupCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.reportPickup(command));
    }

    static CompletionStage<Boolean> confirmItemReceipt(MatchRuntime runtime, ConfirmMatchItemReceiptCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.loot.confirm(command));
    }

    static CompletionStage<Boolean> discardItem(MatchRuntime runtime, DiscardMatchItemCommand command) {
        return runtime == null ? CompletableFuture.completedFuture(false)
            : runtime.turnWork(() -> runtime.items.loot.discard(command));
    }

    boolean reportPickup(ReportMatchPickupCommand command) {
        var context = command.context();

        if (!owner.reportCurrent(context)) {
            return false;
        }

        boolean valid = member(context).slotIndex() == command.slotIndex()
            && command.slotIndex() == command.commandSlot()
            && (command.gaugeSlot().isEmpty() || command.gaugeSlot().orElseThrow() == command.slotIndex())
            && command.entityId() != -1L;
        return ignored(context, command.slotIndex(), MatchLogEvent.PICKUP_IGNORED, valid, "UNSUPPORTED_PICKUP");
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

    boolean activate(UseMatchItemCommand command) {
        if (!current(command.context(), command.turnRevision(), command.phaseRevision(), command.slot())) {
            return false;
        }

        var member = member(command.context());
        boolean card = command.itemId() != 6 && MatchItemSelectionRules.supports(command.itemId());
        boolean route = command.flag12() == 0 && command.flag13() == 0 && command.team() == team(member)
            && pending == null;

        if (!route || (card ? !policy.allowsUse(command.itemId()) || command.inventoryIndex() < 0
            || command.inventoryIndex() >= BattleInventory.CAPACITY
            : command.itemId() != 6 || command.inventoryIndex() != 255)) {
            return reject("INVALID_ITEM_ACTIVATION", command.itemId());
        }

        String refusal = null;
        boolean duplicate;
        int before;
        int after;

        synchronized (admission) {
            if (!owner.admits(command.context())) {
                return false;
            }

            var selected = match.attacks().getOrDefault(member.sessionId(), MatchAttackSelection.NORMAL);
            var inventory = match.loot().inventory(member.sessionId());
            duplicate = card
                ? inventory.slots().get(command.inventoryIndex()) == BattleInventory.EMPTY
                    && selected.duplicate(command.inventoryIndex(), command.itemId())
                : selected.activeItems().contains(6);
            before = match.actors().get(member.sessionId()).energy();

            if (!duplicate) {
                if (selected.activeItems().size() >= MatchAttackSelection.MAX_ACTIVATIONS) {
                    refusal = "ACTIVE_ITEM_LIMIT";
                } else if (!match.useItem(member.sessionId(), command.inventoryIndex(), command.itemId())) {
                    refusal = card ? "INVALID_INVENTORY_ITEM" : "INSUFFICIENT_SPECIAL_ENERGY";
                } else {
                    owner.refresh();
                }
            }

            after = match.actors().get(member.sessionId()).energy();
        }

        if (refusal != null) {
            if (refusal.equals("ACTIVE_ITEM_LIMIT")) {
                warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_ACTIVE_ITEM_LIMIT");
            }

            return reject(refusal, command.itemId());
        }

        var event = switch (command.itemId()) {
            case 1, 2 -> duplicate ? MatchLogEvent.REPEAT_DUPLICATE : MatchLogEvent.REPEAT_ACTIVATED;
            case 3 -> duplicate ? MatchLogEvent.SPREAD_DUPLICATE : MatchLogEvent.SPREAD_ACTIVATED;
            case 4 -> duplicate ? MatchLogEvent.NUCLEAR_DUPLICATE : MatchLogEvent.NUCLEAR_ACTIVATED;
            case 5 -> duplicate ? MatchLogEvent.POWER_DUPLICATE : MatchLogEvent.POWER_ACTIVATED;
            case 7 -> duplicate ? MatchLogEvent.GUIDED_DUPLICATE : MatchLogEvent.GUIDED_ACTIVATED;
            case 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050 ->
                duplicate ? MatchLogEvent.DAMAGE_UP_DUPLICATE : MatchLogEvent.DAMAGE_UP_ACTIVATED;
            default -> duplicate ? MatchLogEvent.SPECIAL_DUPLICATE : MatchLogEvent.SPECIAL_ACTIVATED;
        };
        LOG.log(
            event,
            matchId(match.id().value()),
            slotIndex(command.slot()),
            sessionId(command.context().sessionId()),
            admissionId(command.context().admissionId()),
            channelId(match.channelId()),
            turnRevision(command.turnRevision()),
            itemId(command.itemId()),
            inventoryIndex(command.inventoryIndex()),
            energyBefore(before),
            energyAfter(after)
        );

        if (!duplicate) {
            deliver(output -> output.useItem(command));
        }

        return true;
    }

    boolean record(RecordMatchSpendCommand command) {
        if (!current(command.context(), command.turnRevision(), command.phaseRevision(), command.slot())) {
            return false;
        }

        var member = member(command.context());
        int team = team(member);
        var selection = match.attacks().getOrDefault(member.sessionId(), MatchAttackSelection.NORMAL);
        var authorized = new ArrayList<>(selection.activeItems());
        boolean valid = (command.teamClear11() == team || command.teamClear11() == 255)
            && (command.teamClear12() == team || command.teamClear12() == 255)
            && command.items().size() <= 3;

        for (var item : command.items()) {
            valid &= item.scalar2() >= 0 && item.scalar2() <= 255
                && authorized.remove(Integer.valueOf(item.itemId()));
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
        var actor = match.participants().stream().filter(p -> p.slotIndex() == match.turn().activeSlot())
            .findFirst().orElseThrow();

        if (!match.attacks().getOrDefault(actor.sessionId(), MatchAttackSelection.NORMAL).activeItems().isEmpty()
            && pending == null) {
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
        return reject(reason, 6);
    }

    private boolean reject(String reason, int item) {
        var event = switch (item) {
            case 1, 2 -> MatchLogEvent.REPEAT_REJECTED;
            case 3 -> MatchLogEvent.SPREAD_REJECTED;
            case 4 -> MatchLogEvent.NUCLEAR_REJECTED;
            case 5 -> MatchLogEvent.POWER_REJECTED;
            case 7 -> MatchLogEvent.GUIDED_REJECTED;
            case 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050 -> MatchLogEvent.DAMAGE_UP_REJECTED;
            default -> MatchLogEvent.SPECIAL_REJECTED;
        };
        LOG.log(
            event,
            matchId(match.id().value()),
            itemId(item),
            reason(reason)
        );
        owner.terminate(RoomMatchEndReason.INVALID_SCENARIO);
        return false;
    }

    void deliver(Function<MatchOutput, CompletionStage<Void>> delivery) {
        try {
            for (var recipient : owner.recipients()) {
                if (recipientCurrent(recipient)) {
                    observe(delivery.apply(recipient.output()));
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

    boolean recipientCurrent(MatchSubscription recipient) {
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

    void observe(CompletionStage<Void> delivery) {
        delivery.handle(LogContext.propagate((ignored, failure) -> {
            if (failure != null) {
                owner.turnWork(() -> deliveryFailed(failure), () -> deliveryFailed(failure));
            }

            return null;
        }));
    }

    boolean deliveryFailed(Throwable failure) {
        if (!owner.running() || failed) {
            return false;
        }

        failed = true;
        LOG.failure(MatchLogEvent.ITEM_DELIVERY_FAILED, failure, matchId(match.id().value()));
        owner.terminate(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED);
        return true;
    }
}
