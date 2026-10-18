package taan.online.room.protocol.packet;

public record PqGameSessionCancelChief(long characterId) {
    public static final int TYPE_ID = 0x7c9309a3;
}
