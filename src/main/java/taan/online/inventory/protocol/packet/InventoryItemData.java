package taan.online.inventory.protocol.packet;

public record InventoryItemData(
    long instanceId,
    int slot,
    long itemId,
    int quantity,
    int parameter0,
    int parameter1,
    int parameter2
) {
}
