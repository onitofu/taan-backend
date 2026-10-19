package taan.online.match.model;

public record MovementAllowance(
    long turnRevision,
    long startedAtNanos,
    long observedAtNanos,
    int initialBudgetMillis,
    int spentMillis,
    int stepPeriodMillis
) {
}
