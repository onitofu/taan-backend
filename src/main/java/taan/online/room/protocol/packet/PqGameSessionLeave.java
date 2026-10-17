package taan.online.room.protocol.packet;

public record PqGameSessionLeave(int roomId, int reason) {
    public static final int TYPE_ID = 0x48dd076b;
}
