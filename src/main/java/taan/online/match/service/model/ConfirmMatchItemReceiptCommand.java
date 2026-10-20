package taan.online.match.service.model;

public record ConfirmMatchItemReceiptCommand(
    MatchCommandContext context,
    int slotIndex,
    int inventoryIndex,
    int itemId
) {
}
