package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.model.RoomMatchContext;

public final class ConfirmMatchLoadingAction {
    private final RoomMatchAccess rooms;

    public ConfirmMatchLoadingAction(RoomMatchAccess rooms) {
        this.rooms = rooms;
    }

    public CompletionStage<Void> execute(UUID id, RoomMatchContext context, int slot, int x, int y) {
        return context != null && context.membership().sessionId().equals(id)
            ? rooms.loadingDone(context, slot, x, y) : CompletableFuture.completedFuture(null);
    }
}
