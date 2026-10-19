package taan.online.match.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

record MatchTerminationDispatch(MatchRuntime runtime, MatchTerminationDelivery delivery) {
    CompletionStage<Void> dispatch(UUID requester, Runnable queued) {
        var sending = new CompletableFuture<CompletionStage<Void>>();
        return runtime.finish(() -> {
            var stage = delivery.dispatch(requester);
            queued.run();
            sending.complete(stage);
        }).thenCompose(ignored -> sending.thenCompose(value -> value));
    }
}
