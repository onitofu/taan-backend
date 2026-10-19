package taan.online.match.service.model;

import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchToken;

public record MatchDeadlineExpiredCommand(RoomMatchProgressToken progress) implements MatchStateCommand {
    @Override
    public RoomMatchToken token() {
        return progress.match();
    }
}
