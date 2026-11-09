package taan.online.match.model;

import java.util.Optional;
import java.util.UUID;

public record MatchGhostState(
    UUID sessionId,
    int slotIndex,
    GhostPoint position,
    GhostPoint target,
    int facing,
    Optional<GhostSegment> segment,
    boolean queuedForShot
) {
    public MatchGhostState target(GhostPoint value) {
        return new MatchGhostState(sessionId, slotIndex, position, value, facing, segment, queuedForShot);
    }

    public MatchGhostState advance(long tick) {
        if (segment.isEmpty()) {
            return this;
        }

        var motion = segment.orElseThrow();
        return new MatchGhostState(
            sessionId,
            slotIndex,
            GhostMovementRules.positionAt(motion, tick),
            target,
            motion.facing(),
            tick >= motion.endTick() ? Optional.empty() : segment,
            queuedForShot
        );
    }

    public MatchGhostState completed() {
        return new MatchGhostState(
            sessionId,
            slotIndex,
            segment.map(GhostSegment::end).orElse(position),
            target,
            segment.map(GhostSegment::facing).orElse(facing),
            Optional.empty(),
            false
        );
    }
}
