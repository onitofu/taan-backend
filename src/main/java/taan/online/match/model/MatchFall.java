package taan.online.match.model;

public record MatchFall(
    int slot,
    long tick,
    int duration,
    int reportedStartY,
    int endY,
    int effectiveStartY
) {
}
