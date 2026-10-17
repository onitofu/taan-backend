package taan.online.inventory.service.model;

import taan.online.inventory.model.InventoryMove;

public record MoveInventoryItemCommand(long accountId, InventoryMove move) {
}
