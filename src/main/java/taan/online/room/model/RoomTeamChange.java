package taan.online.room.model;

public record RoomTeamChange(RoomSlot source, RoomSlot destination) {
    public RoomTeamChange {
        if (source.state() != RoomSlotState.OCCUPIED || destination.state() != RoomSlotState.OCCUPIED
            || !source.participant().equals(destination.participant()) || source.team() == destination.team()) {
            throw new IllegalArgumentException("Invalid room team change");
        }
    }
}
