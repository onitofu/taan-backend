package taan.online.inventory.model;

import java.util.List;
import taan.online.account.model.AccountGender;

public record InventoryState(List<CharacterInventory> inventories) {
    public InventoryState {
        inventories = List.copyOf(inventories);

        if (inventories.size() != InventoryKind.values().length
            || inventories.stream().map(CharacterInventory::kind).distinct().count() != inventories.size()
            || inventories.stream().map(CharacterInventory::id).distinct().count() != inventories.size()) {
            throw new IllegalArgumentException("Incomplete or duplicate inventory containers");
        }

        var items = inventories.stream().flatMap(value -> value.items().stream()).toList();

        if (items.stream().map(InventoryItem::id).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Duplicate item identity");
        }
    }

    public InventoryMoveDecision move(InventoryMove move, ItemCatalog catalog, AccountGender gender) {
        var source = inventories.stream().filter(value -> value.id().value() == move.sourceInventoryId())
            .findFirst().orElse(null);
        var destination = inventories.stream().filter(value -> value.id().value() == move.destinationInventoryId())
            .findFirst().orElse(null);

        if (source == null || destination == null) {
            return rejected(InventoryMoveOutcome.UNKNOWN_CONTAINER);
        }

        if (source.kind() == InventoryKind.JEWEL_BOX || destination.kind() == InventoryKind.JEWEL_BOX) {
            return rejected(InventoryMoveOutcome.UNSUPPORTED_CONTAINER);
        }

        if (move.sourceSlot() < 0 || move.sourceSlot() >= source.kind().capacity()
            || move.destinationSlot() < 0 || move.destinationSlot() >= destination.kind().capacity()) {
            return rejected(InventoryMoveOutcome.INVALID_SLOT);
        }

        var item = source.items().stream().filter(value -> value.slot() == move.sourceSlot()).findFirst().orElse(null);
        var target = destination.items().stream().filter(value -> value.slot() == move.destinationSlot())
            .findFirst().orElse(null);

        if (item == null || item.id().value() != move.instanceId() || item.itemId() != move.sourceItemId()) {
            return rejected(InventoryMoveOutcome.STALE_SOURCE);
        }

        if ((target == null ? 0 : target.itemId()) != move.destinationObservedItemId()) {
            return rejected(InventoryMoveOutcome.STALE_DESTINATION);
        }

        if (source.id().equals(destination.id()) && move.sourceSlot() == move.destinationSlot()) {
            return rejected(InventoryMoveOutcome.NO_CHANGE);
        }

        if (item.quantity() != 1 || target != null && target.quantity() != 1) {
            return rejected(InventoryMoveOutcome.UNSUPPORTED_QUANTITY);
        }

        if (!compatible(item, destination, move.destinationSlot(), catalog, gender)
            || target != null && !compatible(target, source, move.sourceSlot(), catalog, gender)) {
            return rejected(InventoryMoveOutcome.INCOMPATIBLE_EQUIPMENT);
        }

        var placement = new ItemPlacement(item.id(), destination.id(), move.destinationSlot());
        return new InventoryMoveDecision(
            InventoryMoveOutcome.MOVED,
            target == null ? List.of(placement)
                : List.of(placement, new ItemPlacement(target.id(), source.id(), move.sourceSlot()))
        );
    }

    private boolean compatible(
        InventoryItem item,
        CharacterInventory destination,
        int slot,
        ItemCatalog catalog,
        AccountGender gender
    ) {
        return catalog.available(item.itemId())
            && (destination.kind() != InventoryKind.EQUIPMENT || catalog.fits(item.itemId(), slot, gender));
    }

    private InventoryMoveDecision rejected(InventoryMoveOutcome outcome) {
        return new InventoryMoveDecision(outcome, List.of());
    }
}
