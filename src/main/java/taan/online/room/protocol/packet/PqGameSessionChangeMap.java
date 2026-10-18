package taan.online.room.protocol.packet;

public record PqGameSessionChangeMap(int mapId) {
    public static final int TYPE_ID = 0x69d708e2;
}
