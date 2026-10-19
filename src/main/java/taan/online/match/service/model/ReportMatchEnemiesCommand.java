package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchToken;

public record ReportMatchEnemiesCommand(MatchCommandContext context) implements MatchStateCommand {
    @Override
    public RoomMatchToken token() {
        return context.token();
    }
}
