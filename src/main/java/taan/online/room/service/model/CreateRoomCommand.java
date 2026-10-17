package taan.online.room.service.model;

public record CreateRoomCommand(
    int state,
    int roomGrade,
    String title,
    int gameKind,
    int gameType,
    int rule,
    int playerCount,
    int capacity,
    boolean protectedRoom,
    int mapId
) {
    @Override
    public String toString() {
        return "CreateRoomCommand[REDACTED]";
    }
}
