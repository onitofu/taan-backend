package taan.online.match.protocol.packet;

public record PcGamePlayTurnEnd(int slotIndex, int scalar14) {
    public static final int TYPE_ID = 0x3fdb06e2;
}
