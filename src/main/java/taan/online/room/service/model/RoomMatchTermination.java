package taan.online.room.service.model;

import java.util.List;
import java.util.Objects;

public record RoomMatchTermination(
    RoomMatchToken token,
    RoomMatchEndReason reason,
    RoomMatchTerminationMode mode,
    List<RoomMembership> recipients
) {
    public RoomMatchTermination {
        Objects.requireNonNull(token);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(mode);
        recipients = List.copyOf(recipients);
    }
}
