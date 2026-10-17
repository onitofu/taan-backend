package taan.online.room.protocol.packet;

public record GameSessionInfoPack(
    int mask,
    int roomId,
    int state,
    int roomGrade,
    String title,
    int gameKind,
    int gameType,
    int rule,
    int playerCount,
    int capacity,
    int mapId,
    String password
) {
    public static final int TYPE_ID = 0x49660769;
    public static final int FULL_MASK = 0x03ff;
    public static final int DELETE_MASK = 0x8000;

    @Override
    public String toString() {
        return "GameSessionInfoPack[REDACTED]";
    }
}
