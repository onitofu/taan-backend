package taan.online.match.protocol.packet;

public record PcGamePlayUseItem(int slotIndex, int team, int flag12, int flag13, int inventoryIndex, int itemId) {
    public static final int TYPE_ID = 0x3f8506de;
}
