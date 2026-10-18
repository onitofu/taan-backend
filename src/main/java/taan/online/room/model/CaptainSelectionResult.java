package taan.online.room.model;

public record CaptainSelectionResult(int blueLeaderSlot, int redLeaderSlot) {
    public CaptainSelectionResult {
        if (blueLeaderSlot < 0 || blueLeaderSlot > 7 || redLeaderSlot < 0 || redLeaderSlot > 7
            || (blueLeaderSlot & 2) != 0 || (redLeaderSlot & 2) == 0) {
            throw new IllegalArgumentException("Invalid captain slots");
        }
    }
}
