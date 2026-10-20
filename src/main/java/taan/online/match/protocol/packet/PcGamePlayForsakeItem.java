package taan.online.match.protocol.packet;

public record PcGamePlayForsakeItem(int slotIndex, int inventoryIndex, int itemId) {
    public static final String WIRE_NAME = "PcGamePlay_ForsakeItem";
    public static final int TYPE_ID = 0x5f23087c;
}
