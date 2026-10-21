package taan.online.character.protocol.packet;

public record PcUpdateMoney(int localIndex, int kind, long balance, long limitMessageAmount, int showLimitMessage) {
    public static final int TYPE_ID = 0x229b051e;
}
