package taan.online.room.service.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;

public record RoomEntryTarget(RoomId id, UUID generation, int channelId) {
    public static Optional<RoomEntryTarget> quickJoin(
        Collection<Room> rooms,
        int channel,
        RoomParticipant participant
    ) {
        return rooms.stream()
            .filter(room -> room.channelId() == channel && room.state() == RoomState.WAITING)
            .filter(room -> !room.protectedRoom() && room.playerCount() < room.settings().capacity())
            .filter(room -> room.slots().stream().anyMatch(slot -> slot.state() == RoomSlotState.OPEN))
            .filter(room -> !room.contains(participant))
            .min(Comparator.comparingInt(Room::playerCount).reversed().thenComparingInt(room -> room.id().value()))
            .map(room -> new RoomEntryTarget(room.id(), room.generation(), channel));
    }

    public static Optional<RoomEntryTarget> find(Collection<Room> rooms, int channelId, int requestedId) {
        return rooms.stream().filter(room -> room.id().value() == requestedId)
            .filter(room -> room.channelId() == channelId)
            .map(room -> new RoomEntryTarget(room.id(), room.generation(), channelId)).findFirst();
    }
}
