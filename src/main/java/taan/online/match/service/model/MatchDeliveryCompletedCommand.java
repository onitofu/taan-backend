package taan.online.match.service.model;

import java.util.UUID;
import taan.online.room.service.model.RoomMatchProgressToken;
import taan.online.room.service.model.RoomMatchToken;

public record MatchDeliveryCompletedCommand(
    RoomMatchProgressToken progress,
    UUID recipient,
    boolean success
) implements MatchStateCommand {
    @Override
    public RoomMatchToken token() {
        return progress.match();
    }
}
