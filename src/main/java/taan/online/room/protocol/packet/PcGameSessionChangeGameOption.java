package taan.online.room.protocol.packet;

public record PcGameSessionChangeGameOption(int mapId, int gameType, int rule, int missionDifficultyOrIgnored) {
    public static final int TYPE_ID = 0xb13f0ba9;
}
