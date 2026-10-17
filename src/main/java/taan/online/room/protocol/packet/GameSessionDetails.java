package taan.online.room.protocol.packet;

public record GameSessionDetails(
    int roomGrade,
    String title,
    int gameKind,
    int gameType,
    int rule,
    int playerCount,
    int capacity,
    String password,
    int mapId
) {
    @Override
    public String toString() {
        return "GameSessionDetails[REDACTED]";
    }
}
