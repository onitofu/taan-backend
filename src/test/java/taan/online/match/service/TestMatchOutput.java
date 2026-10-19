package taan.online.match.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchOutcome;
import taan.online.match.service.model.MatchSnapshot;

public final class TestMatchOutput implements MatchOutput {
    public final List<MatchSnapshot> starts = new ArrayList<>();
    public final List<MatchSnapshot> turns = new ArrayList<>();
    public CompletableFuture<Void> startDelivery = CompletableFuture.completedFuture(null);
    public CompletableFuture<Void> turnDelivery = CompletableFuture.completedFuture(null);
    public RuntimeException bootstrapFailure;
    public int ends;
    public final List<String> closed = new ArrayList<>();
    public CompletableFuture<Void> delivery = CompletableFuture.completedFuture(null);
    public RuntimeException failure;
    public Runnable onEnd = () -> { };

    @Override
    public CompletionStage<Void> start(MatchSnapshot snapshot) {
        starts.add(snapshot);

        if (bootstrapFailure != null) {
            throw bootstrapFailure;
        }

        return startDelivery;
    }

    @Override
    public CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient) {
        turns.add(snapshot);

        if (bootstrapFailure != null) {
            throw bootstrapFailure;
        }

        return turnDelivery;
    }

    @Override
    public CompletionStage<Void> end(MatchOutcome outcome) {
        ends++;
        onEnd.run();

        if (failure != null) {
            throw failure;
        }

        return delivery;
    }

    @Override
    public void close(String reason) {
        closed.add(reason);
    }
}
