package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public sealed interface MatchCommand permits MatchStateCommand, TerminateMatchCommand {
    RoomMatchToken token();
}
