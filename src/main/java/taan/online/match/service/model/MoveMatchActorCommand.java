package taan.online.match.service.model;

public record MoveMatchActorCommand(
    MatchCommandContext context,
    long turnRevision,
    long phaseRevision,
    int slot,
    int x,
    int y,
    int facing,
    MatchMovementKind kind
) {
}
