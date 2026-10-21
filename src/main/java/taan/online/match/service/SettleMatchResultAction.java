package taan.online.match.service;

import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.damage;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.forceMedal;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchResult;
import taan.online.match.repository.MatchResultRepository;
import taan.online.match.service.model.MatchSnapshot;

public final class SettleMatchResultAction {
    private static final EventLogger LOG = EventLogger.forClass(SettleMatchResultAction.class);

    private final MatchResultRepository results;
    private final Executor executor;
    private final Clock clock;

    public SettleMatchResultAction(MatchResultRepository results, Executor executor, Clock clock) {
        this.results = results;
        this.executor = executor;
        this.clock = clock;
    }

    MatchResult prepare(MatchSnapshot snapshot, MatchOutcome outcome) {
        return MatchResult.finish(
            snapshot.id(),
            clock.instant(),
            outcome,
            snapshot.participants(),
            snapshot.world().damageStatistics(),
            snapshot.resolution().exits()
        );
    }

    CompletionStage<MatchResult> execute(MatchResult result) {
        var completed = new CompletableFuture<MatchResult>();
        long started = System.nanoTime();

        var task = LogContext.propagate(() -> settle(result, completed, started));

        try {
            executor.execute(task);
        } catch (RuntimeException failure) {
            failed(result, completed, failure, started);
        }

        return completed;
    }

    private void settle(MatchResult result, CompletableFuture<MatchResult> completed, long started) {
        try {
            var stored = results.settle(result);
            var event = stored.created() ? MatchLogEvent.REWARDS_COMMITTED : MatchLogEvent.REWARDS_DUPLICATE;

            for (var participant : stored.result().participants()) {
                LOG.log(
                    event,
                    matchId(stored.result().matchId().value()),
                    characterId(participant.characterId().value()),
                    damage(participant.damage()),
                    forceMedal(participant.forceMedal()),
                    durationMs(elapsed(started))
                );
            }

            completed.complete(stored.result());
        } catch (RuntimeException failure) {
            failed(result, completed, failure, started);
        }
    }

    private void failed(
        MatchResult result,
        CompletableFuture<MatchResult> completed,
        RuntimeException failure,
        long start
    ) {
        LOG.failure(
            MatchLogEvent.REWARDS_FAILED,
            failure,
            matchId(result.matchId().value()),
            reason("SETTLEMENT_FAILED"),
            durationMs(elapsed(start))
        );
        completed.completeExceptionally(failure);
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
