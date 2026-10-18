package taan.online.room.protocol.packet;

public record PcGameSessionReady(long accountId, boolean ready) {
    public static final int TYPE_ID = 0x47ef0765;
}
