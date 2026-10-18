package taan.online.room.service.model;

import java.util.Objects;
import taan.online.room.model.RoomSlot;

public record RoomMatchParticipant(RoomMembership membership, RoomSlot slot) {
    public RoomMatchParticipant {
        Objects.requireNonNull(membership);
        Objects.requireNonNull(slot);

        if (slot.participant() == null || !membership.sessionId().equals(slot.participant().sessionId())) {
            throw new IllegalArgumentException("Invalid match admission");
        }
    }
}
