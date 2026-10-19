package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchContext;

public final class ConfirmMatchLoadingAction {
    private final MatchRegistry matches;

    public ConfirmMatchLoadingAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Void> execute(UUID id, RoomMatchContext context, int slot, int x, int y) {
        return context != null && context.membership().sessionId().equals(id)
            ? matches.loadingDone(context, slot, x, y).thenCompose(
                next -> next == null ? CompletableFuture.completedFuture(null) : matches.publish(next.match())
            ) : CompletableFuture.completedFuture(null);
    }
}
