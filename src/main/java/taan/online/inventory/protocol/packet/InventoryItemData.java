package taan.online.inventory.protocol.packet;

public record InventoryItemData(
    long instanceId,
    int slot,
    long itemId,
    int quantity,
    int palette1Id,
    int palette2Id,
    int palette3Id
) {
}
