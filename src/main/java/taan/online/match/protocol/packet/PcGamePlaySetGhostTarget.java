package taan.online.match.protocol.packet;

public record PcGamePlaySetGhostTarget(int slotIndex, int x, int y) {
    public static final String WIRE_NAME = "PcGamePlay_SetGhostTarget";
    public static final int TYPE_ID = 0x7ab909ba;
}
