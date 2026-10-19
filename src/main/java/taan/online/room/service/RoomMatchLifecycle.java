package taan.online.room.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

public interface RoomMatchLifecycle {
    RoomMatchReservationResult reserve(RoomMatchDraft draft);

    CompletionStage<RoomMatchReservationResult> commit(RoomMatchToken token);

    RoomMatchReservationResult cancel(RoomMatchToken token, String reason);

    boolean activate(RoomMatchToken token, RoomMatchCallbacks callbacks);

    CompletionStage<Void> publish(RoomMatchToken token);

    CompletionStage<Boolean> requestDeparture(RoomMatchToken token, RoomMembership expected, RoomMatchEndReason reason);

    RoomMatchTermination prepareTermination(RoomMatchToken token, RoomMatchEndReason reason, UUID departing);

    CompletionStage<Void> dispatchTermination(RoomMatchTermination terminal, UUID requester, Runnable queued);

    RoomMatchToken resultToken(RoomMembership membership);

    void release(RoomMembership membership);
}
