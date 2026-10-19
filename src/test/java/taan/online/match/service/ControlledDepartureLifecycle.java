package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.RoomMatchCallbacks;
import taan.online.room.service.RoomMatchLifecycle;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

final class ControlledDepartureLifecycle implements RoomMatchLifecycle {
    private final RoomMatchLifecycle delegate;
    RuntimeException directFailure;
    final CompletableFuture<Boolean> departure = new CompletableFuture<>();

    ControlledDepartureLifecycle(RoomMatchLifecycle delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletionStage<Boolean> requestDeparture(
        RoomMatchToken token,
        RoomMembership expected,
        RoomMatchEndReason reason
    ) {
        if (directFailure != null) {
            throw directFailure;
        }

        return departure;
    }

    @Override
    public RoomMatchReservationResult reserve(RoomMatchDraft draft) {
        return delegate.reserve(draft);
    }

    @Override
    public CompletionStage<RoomMatchReservationResult> commit(RoomMatchToken token) {
        return delegate.commit(token);
    }

    @Override
    public RoomMatchReservationResult cancel(RoomMatchToken token, String reason) {
        return delegate.cancel(token, reason);
    }

    @Override
    public boolean activate(RoomMatchToken token, RoomMatchCallbacks callbacks) {
        return delegate.activate(token, callbacks);
    }

    @Override
    public CompletionStage<Void> publish(RoomMatchToken token) {
        return delegate.publish(token);
    }

    @Override
    public RoomMatchTermination prepareTermination(RoomMatchToken token, RoomMatchEndReason reason, UUID departing) {
        return delegate.prepareTermination(token, reason, departing);
    }

    @Override
    public CompletionStage<Void> dispatchTermination(RoomMatchTermination terminal, UUID requester, Runnable queued) {
        return delegate.dispatchTermination(terminal, requester, queued);
    }

    @Override
    public RoomMatchToken resultToken(RoomMembership membership) {
        return delegate.resultToken(membership);
    }

    @Override
    public void release(RoomMembership membership) {
        delegate.release(membership);
    }
}
