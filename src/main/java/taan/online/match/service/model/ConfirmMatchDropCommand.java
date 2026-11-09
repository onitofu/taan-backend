package taan.online.match.service.model;

public record ConfirmMatchDropCommand(MatchCommandContext context, int slotIndex, int x, int y) {
}
