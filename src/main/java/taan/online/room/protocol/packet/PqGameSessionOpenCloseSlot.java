package taan.online.room.protocol.packet;

public record PqGameSessionOpenCloseSlot(int selector, int slotIndex) {
    public static final int TYPE_ID = 0x92800aa8;
}
