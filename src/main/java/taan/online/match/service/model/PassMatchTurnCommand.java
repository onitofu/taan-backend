package taan.online.match.service.model;

public record PassMatchTurnCommand(
    MatchCommandContext context,
    long turnRevision,
    long phaseRevision,
    int slot,
    int x,
    int y,
    int facing,
    long scalar20
) {
}
