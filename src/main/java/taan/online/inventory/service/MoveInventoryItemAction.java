package taan.online.inventory.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.destinationInventoryId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.instanceId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.sourceInventoryId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.inventory.logging.InventoryLogEvent;
import taan.online.inventory.model.InventoryKind;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.model.ItemCatalog;
import taan.online.inventory.repository.InventoryRepository;
import taan.online.inventory.repository.InventoryTransaction;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.MoveInventoryItemCommand;
import taan.online.inventory.service.model.MoveInventoryItemResult;
import taan.online.logging.EventLogger;

public final class MoveInventoryItemAction {
    private static final EventLogger LOG = EventLogger.forClass(MoveInventoryItemAction.class);

    private final SelectedInventoryAccess sessions;
    private final InventoryRepository inventories;
    private final ItemCatalog catalog;

    public MoveInventoryItemAction(
        SelectedInventoryAccess sessions,
        InventoryRepository inventories,
        ItemCatalog catalog
    ) {
        this.sessions = sessions;
        this.inventories = inventories;
        this.catalog = catalog;
    }

    public MoveInventoryItemResult execute(UUID id, MoveInventoryItemCommand command) {
        long started = System.nanoTime();
        var selected = sessions.selected(id);

        if (selected.isEmpty()) {
            return closed();
        }

        var actor = selected.orElseThrow();
        MoveInventoryItemResult result;

        try (var transaction = inventories.beginTransaction(actor.accountId(), actor.characterId())) {
            result = executeLocked(actor, command, transaction);
        }

        var outcome = result.outcome();

        if (outcome == InventoryMoveOutcome.SESSION_CLOSED) {
            return result;
        }

        if (outcome == InventoryMoveOutcome.MOVED) {
            LOG.log(
                InventoryLogEvent.MOVE_SUCCEEDED,
                sessionId(id),
                accountId(actor.accountId().value()),
                characterId(actor.characterId()),
                sourceInventoryId(command.move().sourceInventoryId()),
                destinationInventoryId(command.move().destinationInventoryId()),
                instanceId(command.move().instanceId()),
                durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            );
        } else {
            LOG.log(
                InventoryLogEvent.MOVE_REJECTED,
                sessionId(id),
                accountId(actor.accountId().value()),
                characterId(actor.characterId()),
                reason(outcome.name())
            );
        }

        if (!current(actor)) {
            return closed();
        }

        return result;
    }

    private MoveInventoryItemResult executeLocked(
        InventoryActor actor,
        MoveInventoryItemCommand command,
        InventoryTransaction transaction
    ) {
        if (!current(actor)) {
            return closed();
        }

        var state = transaction.state();
        state.inventories().forEach(value -> catalog.validate(value, actor.gender()));
        var decision = state.move(command.move(), catalog, actor.gender());
        var outcome = command.accountId() == actor.accountId().value()
            ? decision.outcome() : InventoryMoveOutcome.ACCOUNT_MISMATCH;

        if (outcome == InventoryMoveOutcome.MOVED) {
            if (!current(actor)) {
                return closed();
            }

            transaction.apply(decision.placements());
            state = transaction.state();

            if (!current(actor)) {
                return closed();
            }
        }

        transaction.commit();

        var move = command.move();
        var snapshots = state.inventories().stream().filter(value -> value.kind() != InventoryKind.JEWEL_BOX)
            .filter(
                value -> outcome == InventoryMoveOutcome.NO_CHANGE ? value.id().value() == move.sourceInventoryId()
                    : outcome != InventoryMoveOutcome.MOVED || value.id().value() == move.sourceInventoryId()
                        || value.id().value() == move.destinationInventoryId()
            )
            .sorted(
                (left, right) -> Boolean.compare(
                    left.id().value() != move.sourceInventoryId(),
                    right.id().value() != move.sourceInventoryId()
                )
            ).toList();
        return new MoveInventoryItemResult(outcome, snapshots);
    }

    private boolean current(InventoryActor actor) {
        return sessions.selected(actor.sessionId()).filter(actor::equals).isPresent();
    }

    private MoveInventoryItemResult closed() {
        return new MoveInventoryItemResult(InventoryMoveOutcome.SESSION_CLOSED, List.of());
    }
}
