package taan.online.room.model;

import java.util.Objects;
import java.util.UUID;

public final class Room {
    private final RoomId id;
    private final UUID generation;
    private final int channelId;
    private final RoomSettings settings;
    private final RoomParticipant owner;
    private RoomState state = RoomState.WAITING;

    public Room(RoomId id, UUID generation, int channelId, RoomSettings settings, RoomParticipant owner) {
        this.id = Objects.requireNonNull(id);
        this.generation = Objects.requireNonNull(generation);
        this.settings = Objects.requireNonNull(settings);
        this.owner = Objects.requireNonNull(owner);

        if (channelId < 1 || channelId > 65535) {
            throw new IllegalArgumentException("Invalid room channel identity");
        }

        this.channelId = channelId;
    }

    public RoomId id() {
        return id;
    }

    public UUID generation() {
        return generation;
    }

    public int channelId() {
        return channelId;
    }

    public RoomSettings settings() {
        return settings;
    }

    public RoomParticipant owner() {
        return owner;
    }

    public RoomState state() {
        return state;
    }

    public boolean close() {
        if (state == RoomState.CLOSED) {
            return false;
        }

        state = RoomState.CLOSED;
        return true;
    }
}
