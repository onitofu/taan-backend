package taan.online.room.service.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public static Map<Integer, List<RoomListing>> replaceChannel(
        Map<Integer, List<RoomListing>> previous,
        Collection<Room> rooms,
        int channelId
    ) {
        var updated = new HashMap<>(previous);
        var snapshot = rooms.stream().filter(room -> room.channelId() == channelId)
            .sorted(Comparator.comparingInt(room -> room.id().value())).map(RoomListing::from).toList();

        if (snapshot.isEmpty()) {
            updated.remove(channelId);
        } else {
            updated.put(channelId, snapshot);
        }

        return Map.copyOf(updated);
    }

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
