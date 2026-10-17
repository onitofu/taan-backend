package taan.online.room.model;

public record RoomId(int value) {
    public RoomId {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("Room identity is outside its range");
        }
    }
}
