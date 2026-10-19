package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchEndReason;

public record DepartMatchCommand(MatchCommandContext context, RoomMatchEndReason reason) {
}
