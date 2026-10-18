package taan.online.room.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.service.model.SetRoomReadyOutcome;
import taan.online.room.service.model.SetRoomReadyResult;

public final class SetRoomReadyAction {
    private static final EventLogger LOG = EventLogger.forClass(SetRoomReadyAction.class);

    private final RoomRegistry rooms;

    public SetRoomReadyAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public SetRoomReadyResult execute(UUID id, boolean ready) {
        var result = rooms.setReady(id, ready);

        if (result.outcome() == SetRoomReadyOutcome.REJECTED) {
            LOG.log(RoomLogEvent.READY_REJECTED, sessionId(id), reason(result.reason()));
        }

        return result;
    }
}
