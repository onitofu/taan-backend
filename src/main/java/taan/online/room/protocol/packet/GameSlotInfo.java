package taan.online.room.protocol.packet;

public record GameSlotInfo(int slotIndex, int state, GameSlotOccupant occupant) {
    public static final int TYPE_ID = 0x1d6104a8;
}
