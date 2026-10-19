package taan.online.match.service.model;

public record ActivateSpecialCommand(
    MatchCommandContext context,
    long turnRevision,
    long phaseRevision,
    int slot,
    int team,
    int flag12,
    int flag13,
    int inventoryIndex,
    int itemId
) {
}
