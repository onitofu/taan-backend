package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchContext;

public final class ReportMatchEnemiesAction {
    private final MatchRegistry matches;

    public ReportMatchEnemiesAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<Boolean> execute(UUID id, RoomMatchContext context) {
        return context != null && context.membership().sessionId().equals(id)
            ? matches.reportEnemies(context) : CompletableFuture.completedFuture(false);
    }
}
