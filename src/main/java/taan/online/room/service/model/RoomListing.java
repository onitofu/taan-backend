package taan.online.room.service.model;

import java.util.Objects;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTitle;

public record RoomListing(
    RoomId id,
    int channelId,
    RoomTitle title,
    RoomState state,
    RoomMode mode,
    RoomRule rule,
    int playerCount,
    int capacity,
    int mapId,
    boolean protectedRoom
) {
    public static RoomListing from(Room room) {
        return new RoomListing(
            room.id(),
            room.channelId(),
            room.settings().title(),
            room.state(),
            room.settings().mode(),
            room.settings().rule(),
            room.playerCount(),
            room.settings().capacity(),
            room.settings().mapId(),
            room.protectedRoom()
        );
    }

    public RoomListing {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        Objects.requireNonNull(state);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(rule);
    }
}
