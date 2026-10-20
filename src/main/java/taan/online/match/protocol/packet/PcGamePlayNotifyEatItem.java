package taan.online.match.protocol.packet;

public record PcGamePlayNotifyEatItem(int slotIndex, int inventoryIndex, int itemId) {
    public static final String WIRE_NAME = "PcGamePlay_NotifyEatItem";
    public static final int TYPE_ID = 0x715e0944;
}
