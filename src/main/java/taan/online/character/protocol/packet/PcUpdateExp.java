package taan.online.character.protocol.packet;

public record PcUpdateExp(int localIndex, int level, long currentExp, long levelStartExp, long nextLevelExp) {
    public static final int TYPE_ID = 0x18d40443;
}
