package taan.online.room.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.room.service.model.SlotAvailabilityResult;

public final class SetRoomSlotAvailabilityAction {
    private static final EventLogger LOG = EventLogger.forClass(SetRoomSlotAvailabilityAction.class);

    private final RoomRegistry rooms;

    public SetRoomSlotAvailabilityAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public SlotAvailabilityResult execute(UUID id, int index, RoomSlotState desiredState) {
        var result = rooms.setSlotAvailability(id, index, desiredState);

        if (result.outcome() == SlotAvailabilityOutcome.REJECTED) {
            LOG.log(RoomLogEvent.SLOT_AVAILABILITY_REJECTED, sessionId(id), slotIndex(index), reason(result.reason()));
        }

        return result;
    }
}
