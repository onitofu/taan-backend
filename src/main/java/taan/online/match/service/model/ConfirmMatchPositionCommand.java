package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public record ConfirmMatchPositionCommand(
    MatchCommandContext context,
    int slot,
    int x,
    int y,
    int facing
) implements MatchStateCommand {
    @Override
    public RoomMatchToken token() {
        return context.token();
    }
}
