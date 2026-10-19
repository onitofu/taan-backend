package taan.online.match.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioAdmissionResult;

final class ScenarioRelay {
    private final MatchAdmissionAccess access;

    ScenarioRelay(MatchAdmissionAccess access) {
        this.access = access;
    }

    CompletionStage<Void> deliver(
        ScenarioAdmissionResult result,
        MatchSnapshot match,
        List<MatchSubscription> subscriptions
    ) {
        if (result.outcome() != ScenarioAdmissionOutcome.ACCEPTED) {
            return CompletableFuture.completedFuture(null);
        }

        var accepted = result.accepted().orElseThrow();
        var sender = accepted.sender();
        boolean phase = match.phase() == MatchPhase.ACTION || match.phase() == MatchPhase.COMPLETION;

        if (!phase || !match.id().equals(sender.matchId()) || !match.roomId().equals(sender.roomId())
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
            if (subscription.sessionId().equals(sender.sessionId())) {
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

            if (access.current(context, member)) {
                deliveries.add(subscription.output().scenario(accepted).toCompletableFuture());
            }
        }

        return CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new));
    }
}
