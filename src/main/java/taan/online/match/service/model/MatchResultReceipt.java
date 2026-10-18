package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

public record MatchResultReceipt(RoomMatchToken token, RoomMembership membership) {
}
