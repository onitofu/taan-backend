package taan.online.match.protocol.packet;

public record PcGamePlayMove(int slotIndex, int x, int y, int facing) {
    public static final int TYPE_ID = 0x2c3c05b9;
}
