package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public record ConfirmMatchLoadingCommand(
    MatchCommandContext context,
    int slot,
    int x,
    int y
) implements MatchStateCommand {
    @Override
    public RoomMatchToken token() {
        return context.token();
    }
}
