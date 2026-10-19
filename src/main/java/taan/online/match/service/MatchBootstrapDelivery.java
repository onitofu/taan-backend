package taan.online.match.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchDeadline;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.service.model.RoomMatchProgressToken;

final class MatchBootstrapDelivery {
    private static final EventLogger LOG = EventLogger.forClass(MatchBootstrapDelivery.class);

    private final MatchSnapshot snapshot;
    private final List<MatchSubscription> recipients;
    private final MatchRuntime callbacks;
    private final MatchScheduler scheduler;
    private final RoomMatchProgressToken token;
    private final WarningLogLimiter warnings;

    MatchBootstrapDelivery(
        MatchSnapshot snapshot,
        List<MatchSubscription> recipients,
        MatchRuntime callbacks,
        MatchScheduler scheduler,
        RoomMatchProgressToken token,
        WarningLogLimiter warnings
    ) {
        this.snapshot = snapshot;
        this.recipients = List.copyOf(recipients);
        this.callbacks = callbacks;
        this.scheduler = scheduler;
        this.token = token;
        this.warnings = warnings;
    }

    CompletionStage<Void> dispatch(UUID requester) {
        scheduler.cancel(token.match().matchId());
        scheduler.schedule(
            new MatchDeadline(token, snapshot.deadlineNanos()),
            () -> callbacks.deadlineExpired(token),
            this::closeAll
        );

        if (!callbacks.beginPublication(snapshot) || snapshot.phase() == MatchPhase.PREVIEW) {
            return CompletableFuture.completedFuture(null);
        }

        CompletionStage<Void> result = CompletableFuture.completedFuture(null);

        for (var recipient : recipients) {
            var delivery = send(recipient);

            if (recipient.sessionId().equals(requester)) {
                result = delivery;
            }
        }

        return result;
    }

    private CompletionStage<Void> send(MatchSubscription recipient) {
        try {
            var delivery = snapshot.phase() == MatchPhase.LOADING ? recipient.output().start(snapshot)
                : recipient.output().firstTurn(snapshot, recipient.sessionId());
            return delivery.handle(
                LogContext.propagate(
                    (ignored, failure) -> {
                        completed(recipient, failure);
                        return null;
                    }
                )
            );
        } catch (RuntimeException failure) {
            completed(recipient, failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void completed(MatchSubscription recipient, Throwable failure) {
        scheduler.execute(
            token,
            () -> {
                callbacks.deliveryCompleted(token, recipient.sessionId(), failure == null).thenAccept(
                    handled -> {
                        if (handled && failure != null) {
                            LOG.failure(
                                MatchLogEvent.BOOTSTRAP_DELIVERY_FAILED,
                                failure,
                                matchId(token.match().matchId()),
                                roomId(token.match().roomId().value()),
                                roomGeneration(token.match().generation()),
                                recipientSessionId(recipient.sessionId())
                            );
                        }

                    }
                );
            },
            this::closeAll
        );
    }

    private void closeAll() {
        warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_SCHEDULER_UNAVAILABLE");
        recipients.forEach(value -> value.output().close("MATCH_SCHEDULER_UNAVAILABLE"));
    }
}
