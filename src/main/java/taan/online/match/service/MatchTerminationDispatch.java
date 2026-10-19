package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.room.service.model.RoomMatchTerminationResult;

record MatchTerminationDispatch(MatchRuntime runtime, MatchTerminationDelivery delivery) {
    CompletionStage<Void> dispatch(UUID requester, Runnable queued) {
        var sending = new CompletableFuture<CompletionStage<Void>>();
        var stage = runtime.finish(() -> {
            var deliveryStage = delivery.dispatch(requester);
            queued.run();
            sending.complete(deliveryStage);
        }).thenCompose(ignored -> sending.thenCompose(value -> value));
        runtime.outcomes.complete(new RoomMatchTerminationResult(delivery.terminal(), stage));
        return stage;
    }
}
