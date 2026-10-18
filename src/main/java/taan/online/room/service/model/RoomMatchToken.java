package taan.online.room.service.model;

import java.util.Objects;
import java.util.UUID;
import taan.online.room.model.RoomId;

public record RoomMatchToken(RoomId roomId, UUID generation, UUID matchId) {
    public RoomMatchToken {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(generation);
        Objects.requireNonNull(matchId);
    }
}
