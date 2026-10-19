package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchToken;

public record TerminateMatchCommand(MatchCommandContext context, RoomMatchEndReason reason) implements MatchCommand {
    @Override
    public RoomMatchToken token() {
        return context.token();
    }
}
