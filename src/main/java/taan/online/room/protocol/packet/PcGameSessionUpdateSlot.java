package taan.online.room.protocol.packet;

public record PcGameSessionUpdateSlot(int resetWidget, GameSlotInfo slot, String text) {
    public static final int TYPE_ID = 0x73290975;
}
