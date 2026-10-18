package taan.online.room.service.model;

import java.util.Objects;

public record RoomMatchProgressToken(RoomMatchToken match, long revision) {
    public RoomMatchProgressToken {
        Objects.requireNonNull(match);
    }
}
