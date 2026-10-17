package taan.online.inventory.protocol.packet;

public record PrMoveInventoryItem(long sourceInventoryId, long destinationInventoryId) {
    public static final int TYPE_ID = 0x4b9907b6;
}
