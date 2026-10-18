package taan.online.room.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMatchTerminationResult;

public interface RoomMatchAccess {
    RoomMatchContext matchContext(UUID sessionId);

    CompletionStage<Void> loadingDone(RoomMatchContext context, int slot, int x, int y);

    boolean confirmPosition(RoomMatchContext context, int slot, int x, int y, int facing);

    boolean reportEnemies(RoomMatchContext context);

    RoomMatchTerminationResult terminateMatch(RoomMatchContext expected, RoomMatchEndReason reason);

    RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason);
}
