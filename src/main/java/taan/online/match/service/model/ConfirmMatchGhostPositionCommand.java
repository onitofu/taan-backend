package taan.online.match.service.model;

public record ConfirmMatchGhostPositionCommand(MatchCommandContext context, int slotIndex, int x, int y, int facing) {
}
