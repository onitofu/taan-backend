package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchProgressToken;

public record MatchDeadline(RoomMatchProgressToken token, long deadlineNanos) {
}
