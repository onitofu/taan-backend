package taan.online.match.service.model;

public record DiscardMatchItemCommand(
    MatchCommandContext context,
    int slotIndex,
    int inventoryIndex,
    int itemId
) {
}
