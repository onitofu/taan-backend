package taan.online.inventory.model;

import java.util.List;

public record InventoryMoveDecision(InventoryMoveOutcome outcome, List<ItemPlacement> placements) {
    public InventoryMoveDecision {
        placements = List.copyOf(placements);
    }
}
