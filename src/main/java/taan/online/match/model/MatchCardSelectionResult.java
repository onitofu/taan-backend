package taan.online.match.model;

public record MatchCardSelectionResult(
    MatchCardSelectionOutcome outcome,
    int cardIndex,
    int slotIndex,
    boolean replay
) {
}
