package taan.online.match.protocol.packet;

public record PcGamePlaySetPos(int slotIndex, int x, int y, int facing) {
    public static final int TYPE_ID = 0x38aa0680;
}
