package taan.online.match.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.ScenarioApplicationResult;

final class ScenarioRelay {
    private final MatchAdmissionAccess access;
    private final BooleanSupplier running;

    ScenarioRelay(MatchAdmissionAccess access) {
        this(access, () -> true);
    }

    ScenarioRelay(MatchAdmissionAccess access, BooleanSupplier running) {
        this.access = access;
        this.running = running;
    }

    CompletionStage<Void> deliver(
        ScenarioApplicationResult result,
        MatchSnapshot match,
        List<MatchSubscription> subscriptions
    ) {
        if (result.outcome() != ScenarioApplicationOutcome.APPLIED) {
            return CompletableFuture.completedFuture(null);
        }

        var committed = result.committed().orElseThrow();
        var accepted = committed.scenario();
        var sender = accepted.sender();
        boolean phase = match.phase() == MatchPhase.COMPLETION;
        boolean world = match.world().revision() == committed.snapshot().world().revision()
            && match.world().receipt().map(value -> value.fingerprint().equals(accepted.hash())).orElse(false);

        if (!phase || !world || !match.id().equals(sender.matchId()) || !match.roomId().equals(sender.roomId())
            || !match.roomGeneration().equals(sender.roomGeneration()) || match.channelId() != sender.channelId()
            || match.turn().revision() != accepted.turnRevision()) {
            return CompletableFuture.completedFuture(null);
        }

        var shooter = match.participants().stream().filter(
            member -> member.sessionId().equals(sender.sessionId()) && member.admissionId().equals(sender.admissionId())
        ).findFirst();

        if (shooter.isEmpty() || !access.current(sender, shooter.orElseThrow())) {
            return CompletableFuture.completedFuture(null);
        }

        var deliveries = new ArrayList<CompletableFuture<Void>>();

        for (var subscription : subscriptions) {
            if (subscription.sessionId().equals(sender.sessionId())
                || match.resolution().exits().containsKey(subscription.sessionId())) {
                continue;
            }

            var recipient = match.participants().stream().filter(
                member -> member.sessionId().equals(subscription.sessionId())
            ).findFirst();

            if (recipient.isEmpty()) {
                continue;
            }

            var member = recipient.orElseThrow();
            var context = new MatchCommandContext(
                match.id(),
                match.roomId(),
                match.roomGeneration(),
                match.channelId(),
                member.sessionId(),
                member.admissionId()
            );

            if (running.getAsBoolean() && access.current(context, member)) {
                deliveries.add(subscription.output().scenario(committed).toCompletableFuture());
            }
        }

        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }
}
