package taan.online.room.protocol.packet;

public record PqGameSessionChangeGameOption(int mapId, int gameType, int rule, int missionDifficultyOrIgnored) {
    public static final int TYPE_ID = 0xb2d50bb7;
}
