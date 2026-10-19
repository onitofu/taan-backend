package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

public interface RoomMatchCallbacks {
    boolean startCurrent(RoomMatchToken token);

    RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason, UUID requester);
}
