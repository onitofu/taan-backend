package taan.online.match.model;

public record MatchItemReceipt(
    long grantSequence,
    long turnRevision,
    int inventoryIndex,
    int itemId,
    boolean confirmed
) {
    public MatchItemReceipt acknowledged() {
        return new MatchItemReceipt(grantSequence, turnRevision, inventoryIndex, itemId, true);
    }
}
