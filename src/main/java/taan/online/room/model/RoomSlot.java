package taan.online.room.model;

import java.util.Objects;

public record RoomSlot(int index, RoomSlotState state, RoomParticipant participant) {
    public RoomSlot {
        Objects.requireNonNull(state);

        if (index < 0 || index > 7 || state.occupied() != (participant != null)) {
            throw new IllegalArgumentException("Invalid room slot");
        }
    }

    public RoomTeam team() {
        return (index & 2) == 0 ? RoomTeam.BLUE : RoomTeam.RED;
    }
}
