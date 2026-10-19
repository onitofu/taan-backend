package taan.online.match.service.model;

public record MatchTurnTransition(int previousSlot, MatchSnapshot snapshot, boolean turnEndRequired) {
}
