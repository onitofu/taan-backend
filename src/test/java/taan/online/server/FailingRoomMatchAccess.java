package taan.online.server;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

final class FailingRoomMatchAccess implements RoomMatchAccess {
    private final RoomMatchAccess delegate;

    FailingRoomMatchAccess(RoomMatchAccess delegate) {
        this.delegate = delegate;
    }

    @Override
    public RoomMatchContext matchContext(UUID id) {
        return delegate.matchContext(id);
    }

    @Override
    public CompletionStage<Void> loadingDone(RoomMatchContext context, int slot, int x, int y) {
        return delegate.loadingDone(context, slot, x, y);
    }

    @Override
    public boolean confirmPosition(RoomMatchContext context, int slot, int x, int y, int facing) {
        return delegate.confirmPosition(context, slot, x, y, facing);
    }

    @Override
    public boolean reportEnemies(RoomMatchContext context) {
        return delegate.reportEnemies(context);
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchContext context, RoomMatchEndReason reason) {
        throw new IllegalStateException("secret-account@example.test verifier-secret");
    }

    @Override
    public RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason) {
        throw new UnsupportedOperationException();
    }
}
