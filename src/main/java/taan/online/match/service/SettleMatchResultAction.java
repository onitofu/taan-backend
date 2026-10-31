package taan.online.match.service;

import static taan.online.logging.LogField.cardAmount;
import static taan.online.logging.LogField.cardKind;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.damage;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.experienceAfter;
import static taan.online.logging.LogField.experienceBefore;
import static taan.online.logging.LogField.experienceDelta;
import static taan.online.logging.LogField.forceMedal;
import static taan.online.logging.LogField.goldAfter;
import static taan.online.logging.LogField.goldBefore;
import static taan.online.logging.LogField.goldDelta;
import static taan.online.logging.LogField.killMedal;
import static taan.online.logging.LogField.levelAfter;
import static taan.online.logging.LogField.levelBefore;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchParticipationState;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardPolicies;
import taan.online.match.repository.MatchResultRepository;
import taan.online.match.service.model.MatchSnapshot;

public final class SettleMatchResultAction {
    private static final EventLogger LOG = EventLogger.forClass(SettleMatchResultAction.class);

    private final MatchResultRepository results;
    private final MatchRewardPolicies policies;
    private final Executor executor;
    private final Clock clock;
    private final RandomGenerator random;

    public SettleMatchResultAction(
        MatchResultRepository results,
        Executor executor,
        Clock clock,
        MatchRewardPolicies policies
    ) {
        this(results, executor, clock, new SecureRandom(), policies);
    }

    public SettleMatchResultAction(
        MatchResultRepository results,
        Executor executor,
        Clock clock,
        RandomGenerator random,
        MatchRewardPolicies policies
    ) {
        this.results = results;
        this.policies = policies;
        this.executor = executor;
        this.clock = clock;
        this.random = random;
    }

    MatchResult prepare(MatchSnapshot snapshot, MatchOutcome outcome) {
        return prepare(snapshot, outcome, MatchParticipationState.initial());
    }

    MatchResult prepare(MatchSnapshot snapshot, MatchOutcome outcome, MatchParticipationState participation) {
        var result = MatchResult.finish(
            snapshot.id(),
            clock.instant(),
            outcome,
            snapshot.participants(),
            snapshot.world().combatStatistics(),
            snapshot.resolution().exits(),
            participation
        );
        return result.rewarded(result.participants().stream().map(
            p -> p.awardEligible() && !p.passive() ? p.withDraw(random.nextInt(100)) : p
        ).toList());
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
            var stored = results.settle(result, policies.select(result));
            var event = stored.created() ? MatchLogEvent.REWARDS_COMMITTED : MatchLogEvent.REWARDS_DUPLICATE;

            for (var participant : stored.result().participants()) {
                var reward = participant.experienceReward();
                var gold = participant.goldReward();
                var card = participant.card();
                LOG.log(
                    event,
                    matchId(stored.result().matchId().value()),
                    characterId(participant.characterId().value()),
                    damage(participant.damage()),
                    forceMedal(participant.forceMedal()),
                    killMedal(participant.killMedal()),
                    goldBefore(gold == null ? null : gold.before()),
                    goldAfter(gold == null ? null : gold.after()),
                    goldDelta(gold == null ? null : gold.delta()),
                    cardKind(card == null ? null : card.kind().name()),
                    cardAmount(card == null ? null : card.amount()),
                    experienceBefore(reward == null ? null : reward.experienceBefore()),
                    experienceAfter(reward == null ? null : reward.after().progress().experience()),
                    experienceDelta(reward == null ? null : reward.delta()),
                    levelBefore(reward == null ? null : reward.levelBefore()),
                    levelAfter(reward == null ? null : reward.after().level()),
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
