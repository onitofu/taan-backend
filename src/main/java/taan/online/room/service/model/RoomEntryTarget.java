package taan.online.room.service.model;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;

public record RoomEntryTarget(RoomId id, UUID generation, int channelId) {
    public static Optional<RoomEntryTarget> find(Collection<Room> rooms, int channelId, int requestedId) {
        return rooms.stream().filter(room -> room.id().value() == requestedId)
            .filter(room -> room.channelId() == channelId)
            .map(room -> new RoomEntryTarget(room.id(), room.generation(), channelId)).findFirst();
    }
}
