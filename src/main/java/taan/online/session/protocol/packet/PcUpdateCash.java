package taan.online.session.protocol.packet;

public record PcUpdateCash(long balance, long localSlot) {
    public static final int TYPE_ID = 0x1d380495;
}
