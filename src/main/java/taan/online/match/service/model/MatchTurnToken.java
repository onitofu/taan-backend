package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public record MatchTurnToken(RoomMatchToken match, long turnRevision, long phaseRevision) {
}
