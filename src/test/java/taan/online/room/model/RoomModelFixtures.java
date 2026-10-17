package taan.online.room.model;

import java.util.UUID;
import java.util.stream.IntStream;

public final class RoomModelFixtures {
    public static Room withClosedPlaces(
        RoomId id,
        UUID generation,
        int channelId,
        RoomSettings settings,
        RoomParticipant owner
    ) {
        var slots = IntStream.range(0, 8)
            .mapToObj(index -> new RoomSlot(index, RoomSlotState.CLOSED, null)).toList();
        return new Room(id, generation, channelId, settings, null, owner, slots);
    }
}
