package taan.online.inventory.model;

public record InventoryMove(
    long sourceInventoryId,
    long instanceId,
    int sourceSlot,
    long sourceItemId,
    long destinationInventoryId,
    int destinationSlot,
    long destinationObservedItemId
) {
}
