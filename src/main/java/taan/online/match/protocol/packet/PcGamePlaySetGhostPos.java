package taan.online.match.protocol.packet;

public record PcGamePlaySetGhostPos(int slotIndex, int x, int y, int facing) {
    public static final String WIRE_NAME = "PcGamePlay_SetGhostPos";
    public static final int TYPE_ID = 0x5ee90885;
}
