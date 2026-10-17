package taan.online.protocol.connection.packet;

public record PrFirstPacket(int days, int ticks) {
    public static final int TYPE_ID = 0x231a0522;
}
