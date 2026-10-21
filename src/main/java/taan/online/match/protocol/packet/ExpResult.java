package taan.online.match.protocol.packet;

public record ExpResult(int level, long currentExp, long levelStartExp, long nextLevelExp) {
    public static final int TYPE_ID = 0x119903ac;
}
