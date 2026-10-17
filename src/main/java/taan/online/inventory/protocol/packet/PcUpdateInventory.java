package taan.online.inventory.protocol.packet;

public record PcUpdateInventory(Inventory inventory) {
    public static final int TYPE_ID = 0x3b6206e4;
}
