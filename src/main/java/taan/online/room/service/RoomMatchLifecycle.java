package taan.online.room.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchToken;

public interface RoomMatchLifecycle {
    RoomMatchReservationResult reserve(RoomMatchDraft draft);

    RoomMatchReservationResult commit(RoomMatchToken token);

    RoomMatchReservationResult cancel(RoomMatchToken token, String reason);

    boolean previewStarted(RoomMatchToken token);

    RoomMatchTermination prepareTermination(RoomMatchToken token, RoomMatchEndReason reason, UUID departing);

    CompletionStage<Void> dispatchTermination(RoomMatchTermination terminal, UUID requester);

    RoomMatchToken resultToken(RoomMembership membership);

    void release(RoomMembership membership);
}
