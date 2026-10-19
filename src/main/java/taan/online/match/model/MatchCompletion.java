package taan.online.match.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record MatchCompletion(
    UUID shooterSessionId,
    UUID shooterAdmissionId,
    String fingerprint,
    long turnRevision,
    long worldRevision,
    Set<UUID> awaitingSessions,
    long settleNotBeforeNanos,
    long deadlineNanos,
    long actionPhaseRevision
) {
    public MatchCompletion(
        UUID shooterSessionId,
        UUID shooterAdmissionId,
        String fingerprint,
        long turnRevision,
        long worldRevision,
        Set<UUID> awaitingSessions,
        long settleNotBeforeNanos,
        long deadlineNanos
    ) {
        this(
            shooterSessionId,
            shooterAdmissionId,
            fingerprint,
            turnRevision,
            worldRevision,
            awaitingSessions,
            settleNotBeforeNanos,
            deadlineNanos,
            -1
        );
    }

    public MatchCompletion {
        awaitingSessions = Set.copyOf(awaitingSessions);
    }

    public MatchCompletion acknowledged(UUID session) {
        var remaining = new HashSet<>(awaitingSessions);
        remaining.remove(session);
        return new MatchCompletion(
            shooterSessionId,
            shooterAdmissionId,
            fingerprint,
            turnRevision,
            worldRevision,
            remaining,
            settleNotBeforeNanos,
            deadlineNanos,
            actionPhaseRevision
        );
    }
}
