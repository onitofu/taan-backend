package taan.online.server;

import java.util.UUID;
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
    public boolean previewStarted(RoomMatchToken token) {
        throw new UnsupportedOperationException();
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
