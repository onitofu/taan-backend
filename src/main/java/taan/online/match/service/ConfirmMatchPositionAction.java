package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchContext;

public final class ConfirmMatchPositionAction {
    private final MatchRegistry matches;

    public ConfirmMatchPositionAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(UUID id, RoomMatchContext context, int slot, int x, int y, int facing) {
        return context != null && context.membership().sessionId().equals(id)
            ? matches.confirmPosition(context, slot, x, y, facing) : CompletableFuture.completedFuture(false);
    }
}
