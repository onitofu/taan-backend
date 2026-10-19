package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public record StartMatchCommand(RoomMatchToken token) implements MatchStateCommand {
}
