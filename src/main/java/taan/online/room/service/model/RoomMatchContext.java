package taan.online.room.service.model;

import java.util.Objects;

public record RoomMatchContext(RoomMembership membership, RoomMatchToken token) {
    public RoomMatchContext {
        Objects.requireNonNull(membership);
        Objects.requireNonNull(token);

        if (!membership.roomId().equals(token.roomId()) || !membership.generation().equals(token.generation())) {
            throw new IllegalArgumentException("Invalid match context");
        }
    }
}
