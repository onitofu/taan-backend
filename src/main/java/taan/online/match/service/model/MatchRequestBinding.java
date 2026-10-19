package taan.online.match.service.model;

import java.util.Optional;
import taan.online.room.service.model.RoomMatchContext;

public record MatchRequestBinding(
    RoomMatchContext roomContext,
    long turnRevision,
    long worldRevision,
    Optional<String> fingerprint
) {
}
