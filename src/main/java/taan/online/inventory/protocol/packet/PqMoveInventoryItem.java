package taan.online.inventory.protocol.packet;

public record PqMoveInventoryItem(
    long accountId,
    long sourceInventoryId,
    long instanceId,
    int sourceSlot,
    long sourceItemId,
    long destinationInventoryId,
    int destinationSlot,
    long destinationObservedItemId
) {
    public static final int TYPE_ID = 0x4b8707b5;
}
