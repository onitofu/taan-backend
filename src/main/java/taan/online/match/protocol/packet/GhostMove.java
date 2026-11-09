package taan.online.match.protocol.packet;

public record GhostMove(int slotIndex, int startX, int startY, int targetX, int targetY) {
    public static final int TYPE_ID = 0x1190039c;
}
