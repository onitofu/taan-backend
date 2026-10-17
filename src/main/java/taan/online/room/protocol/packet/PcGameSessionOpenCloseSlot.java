package taan.online.room.protocol.packet;

public record PcGameSessionOpenCloseSlot(int selector, int slotIndex) {
    public static final int TYPE_ID = 0x91140a9a;
}
