package taan.online.protocol.connection.packet;

public record PcFirstAccept(long a, long b, int shift) {
    public static final int TYPE_ID = 0x221a050b;
}
