package taan.online.match.service.model;

public record SetMatchGhostTargetCommand(MatchCommandContext context, int slotIndex, int x, int y) {
}
