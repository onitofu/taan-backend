package taan.online.match.model;

public record MatchTurnClosure(
    MatchTurnClosureReason reason,
    long turnRevision,
    long actionPhaseRevision,
    long stopDrainUntilNanos,
    long landingDeadlineNanos
) {
}
