package taan.online.match.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;

record MatchTerminationDispatch(MatchRuntime runtime, MatchTerminationDelivery delivery) {
    static CompletionStage<Void> dispatch(
        Map<RoomMatchToken, MatchTerminationDispatch> terminations,
        RoomMatchTermination terminal,
        UUID requester,
        Runnable queued
    ) {
        var claimed = new AtomicReference<MatchTerminationDispatch>();
        terminations.computeIfPresent(
            terminal.token(),
            (token, pending) -> {
                if (!pending.delivery().terminal().equals(terminal)) {
                    return pending;
                }

                claimed.set(pending);
                return null;
            }
        );
        var pending = claimed.get();

        if (pending == null) {
            return CompletableFuture.completedFuture(null);
        }

        return pending.dispatch(requester, queued);
    }

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
