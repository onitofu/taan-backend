package taan.online.match.protocol.packet;

public record PcGamePlayMakeGhost(int slotIndex, int x, int y, int facing) {
    public static final String WIRE_NAME = "PcGamePlay_MakeGhost";
    public static final int TYPE_ID = 0x4dc607a5;
}
