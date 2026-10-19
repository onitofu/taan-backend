package taan.online.match.service.model;

import java.util.Optional;
import taan.online.room.service.model.RoomMatchContext;

public record MatchRequestBinding(
    RoomMatchContext roomContext,
    long turnRevision,
    long worldRevision,
    Optional<String> fingerprint,
    long phaseRevision
) {
    public static MatchRequestBinding from(MatchCommandContext context, Optional<MatchSnapshot> snapshot) {
        return new MatchRequestBinding(
            context == null ? null : context.roomContext(),
            snapshot.map(value -> value.turn().revision()).orElse(-1L),
            snapshot.map(value -> value.world().revision()).orElse(-1L),
            snapshot.flatMap(value -> value.completion().map(pending -> pending.fingerprint())),
            snapshot.map(MatchSnapshot::phaseRevision).orElse(-1L)
        );
    }
}
