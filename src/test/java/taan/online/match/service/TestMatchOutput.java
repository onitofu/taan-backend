package taan.online.match.service;

import java.util.ArrayList;
import taan.online.match.service.model.PendingMatchSpend;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchOutcome;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.MatchTurnTransition;

public final class TestMatchOutput implements MatchOutput {
    public final List<MatchMovementNotice> movements = new ArrayList<>();
    public final List<Integer> specials = new ArrayList<>();
    public final List<PendingMatchSpend> spends = new ArrayList<>();
    public final List<Integer> stops = new ArrayList<>();
    public final List<CommittedScenario> scenarios = new ArrayList<>();
    public final List<MatchSnapshot> starts = new ArrayList<>();
    public final List<MatchSnapshot> turns = new ArrayList<>();
    public CompletableFuture<Void> startDelivery = CompletableFuture.completedFuture(null);
    public CompletableFuture<Void> turnDelivery = CompletableFuture.completedFuture(null);
    public RuntimeException bootstrapFailure;
    public int ends;
    public final List<String> closed = new ArrayList<>();
    public CompletableFuture<Void> delivery = CompletableFuture.completedFuture(null);
    public RuntimeException failure;
    public RuntimeException motionFailure;
    public Runnable onSpend = () -> { };
    public Runnable onMovement = () -> { };
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
    public CompletionStage<Void> nextTurn(MatchTurnTransition transition) {
        turns.add(transition.snapshot());
        return turnDelivery;
    }

    @Override
    public CompletionStage<Void> movement(MatchMovementNotice notice) {
        movements.add(notice);
        onMovement.run();

        if (motionFailure != null) {
            throw motionFailure;
        }

        return delivery;
    }

    @Override
    public CompletionStage<Void> stopTurn(int slot) {
        stops.add(slot);
        onMovement.run();

        if (motionFailure != null) {
            throw motionFailure;
        }

        return delivery;
    }

    @Override
    public CompletionStage<Void> special(int slot, int team) {
        specials.add(slot);
        onMovement.run();

        if (motionFailure != null) {
            throw motionFailure;
        }

        return delivery;
    }

    @Override
    public CompletionStage<Void> spend(PendingMatchSpend spend) {
        spends.add(spend);
        onSpend.run();

        if (motionFailure != null) {
            throw motionFailure;
        }

        return delivery;
    }

    @Override
    public CompletionStage<Void> scenario(CommittedScenario scenario) {
        scenarios.add(scenario);
        return delivery;
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
