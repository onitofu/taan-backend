package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMatchTerminationResult;

public interface RoomMatchAccess {
    RoomMatchContext matchContext(UUID sessionId);

    boolean previewStarted(RoomMatchToken token);

    RoomMatchTerminationResult terminateMatch(RoomMatchContext expected, RoomMatchEndReason reason);

    RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason);
}
