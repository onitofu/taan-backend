package taan.online.room.service;

import java.util.List;
import java.util.concurrent.CompletionStage;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.RoomSnapshot;

public interface RoomPresenceOutput {
    CompletionStage<Void> initialize(RoomSnapshot snapshot);

    CompletionStage<Void> update(RoomSnapshot snapshot, List<Integer> changedSlots, boolean ownerChanged);

    CompletionStage<Void> slotAvailability(int slotIndex, RoomSlotState state);

    void close(String reason);
}
