package taan.online.match.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import taan.online.logging.LogContext;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchOutcome;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMatchToken;

record MatchTerminationDispatch(
    MatchRuntime runtime,
    MatchTerminationDelivery delivery,
    SettleMatchResultAction settlements,
    MatchResult result,
    boolean persistent
) {
    static MatchTerminationDispatch prepare(
        MatchRuntime runtime,
        MatchTerminationDelivery delivery,
        SettleMatchResultAction settlements,
        MatchSnapshot snapshot,
        MatchOutcome outcome
    ) {
        return new MatchTerminationDispatch(
            runtime,
            delivery,
            settlements,
            settlements.prepare(snapshot, outcome),
            runtime.live() && delivery.terminal().mode() == RoomMatchTerminationMode.RETURN_TO_ROOM
        );
    }

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
        var cleanup = runtime.finish(() -> sending.complete(publish(requester, queued)));
        var stage = cleanup.thenCompose(ignored -> sending.thenCompose(value -> value));
        runtime.outcomes.complete(new RoomMatchTerminationResult(delivery.terminal(), stage));
        return stage;
    }

    private CompletionStage<Void> publish(UUID requester, Runnable queued) {
        var settlement = persistent ? settlements.execute(result) : CompletableFuture.completedFuture(result);
        return settlement.handle(
            LogContext.propagate(
                (stored, failure) -> {
                    try {
                        if (failure != null) {
                            delivery.settlementFailed();
                            return CompletableFuture.<Void>completedFuture(null);
                        }

                        return delivery.dispatch(requester, stored);
                    } finally {
                        queued.run();
                    }
                }
            )
        ).thenCompose(value -> value);
    }
}
