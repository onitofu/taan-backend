package taan.online.inventory.service.model;

import java.util.List;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryMoveOutcome;

public record MoveInventoryItemResult(InventoryMoveOutcome outcome, List<CharacterInventory> inventories) {
    public MoveInventoryItemResult {
        inventories = List.copyOf(inventories);
    }
}
