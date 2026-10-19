package taan.online.match.controller;

import java.util.Optional;
import taan.online.room.service.model.RoomMatchContext;

public record MatchRequestContext(
    RoomMatchContext roomContext,
    long turnRevision,
    long worldRevision,
    Optional<String> fingerprint,
    long phaseRevision
) {
    public MatchRequestContext(
        RoomMatchContext roomContext,
        long turnRevision,
        long worldRevision,
        Optional<String> fingerprint
    ) {
        this(roomContext, turnRevision, worldRevision, fingerprint, -1);
    }

}
