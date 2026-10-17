package taan.online.room.service.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlot;

public record RoomSnapshot(
    RoomId id,
    UUID generation,
    int channelId,
    RoomSettings settings,
    UUID ownerSessionId,
    List<RoomSlot> slots,
    boolean protectedRoom
) {
    public static RoomSnapshot from(Room room) {
        return new RoomSnapshot(
            room.id(),
            room.generation(),
            room.channelId(),
            room.settings(),
            room.ownerSessionId(),
            room.slots(),
            room.protectedRoom()
        );
    }

    public RoomSnapshot {
        slots = List.copyOf(slots);
    }

    public RoomParticipant owner() {
        return slots.stream().map(RoomSlot::participant).filter(Objects::nonNull)
            .filter(value -> value.sessionId().equals(ownerSessionId)).findFirst().orElseThrow();
    }
}
