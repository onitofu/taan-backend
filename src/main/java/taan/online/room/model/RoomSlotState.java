package taan.online.room.model;

public enum RoomSlotState {
    OPEN, CLOSED, OCCUPIED, READY;

    public boolean occupied() {
        return this == OCCUPIED || this == READY;
    }
}
