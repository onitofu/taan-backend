package taan.online.match.service.model;

import taan.online.match.model.MatchLifecycleEffect;
import taan.online.room.service.model.RoomMatchToken;

public record MatchLifecycleToken(
    RoomMatchToken match,
    long phaseRevision,
    long turnRevision,
    long worldRevision,
    MatchLifecycleEffect effect
) {
    public boolean matches(MatchSnapshot snapshot) {
        return match.equals(new RoomMatchToken(snapshot.roomId(), snapshot.roomGeneration(), snapshot.id().value()))
            && phaseRevision == snapshot.phaseRevision() && turnRevision == snapshot.turn().revision()
            && worldRevision == snapshot.world().revision();
    }
}
