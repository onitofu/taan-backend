package taan.online.match.service;

import static taan.online.logging.LogField.cardIndex;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.slotIndex;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchCardSelection;
import taan.online.match.model.MatchCardSelectionOutcome;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchRewardSelectionNotice;
import taan.online.match.service.model.SelectMatchRewardCommand;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

final class MatchResultSelectionRuntime {
    private static final EventLogger LOG = EventLogger.forClass(MatchResultSelectionRuntime.class);

    private final RoomMatchToken token;
    private final Map<UUID, RoomMembership> memberships;
    private final Map<UUID, Integer> slots;
    private final List<MatchSubscription> outputs;
    private final Predicate<RoomMembership> current;
    private final MatchMailbox mailbox;
    private final MatchCardSelection selection;
    private final RandomGenerator random;
    private final WarningLogLimiter warnings;

    MatchResultSelectionRuntime(
        RoomMatchToken token,
        List<RoomMembership> members,
        List<MatchParticipant> participants,
        List<MatchSubscription> outputs,
        Predicate<RoomMembership> current,
        MatchCommandExecutor executor,
        int capacity,
        RandomGenerator random,
        WarningLogLimiter warnings
    ) {
        this.token = token;
        memberships = members.stream().collect(Collectors.toUnmodifiableMap(RoomMembership::sessionId, m -> m));
        slots = participants.stream().collect(Collectors.toUnmodifiableMap(
            MatchParticipant::sessionId,
            p -> p.slotIndex()
        ));
        this.outputs = List.copyOf(outputs);
        this.current = current;
        mailbox = new MatchMailbox(executor, capacity);
        selection = new MatchCardSelection(
            members.stream().map(m -> slots.get(m.sessionId())).collect(Collectors.toSet())
        );
        this.random = random;
        this.warnings = warnings;
    }

    CompletionStage<Void> select(SelectMatchRewardCommand command) {
        return mailbox.submit(
            () -> choose(command),
            CompletableFuture.<Void>completedFuture(null),
            () -> {
                warnings.warn(WarningEvent.LIMIT_EXCEEDED, "RESULT_QUEUE_FULL");
            }
        ).thenCompose(stage -> stage);
    }

    void close() {
        mailbox.terminate(() -> true);
    }

    private CompletionStage<Void> choose(SelectMatchRewardCommand command) {
        var expected = memberships.get(command.sessionId());

        if (expected == null
            || !expected.equals(command.context().membership()) || !current.test(expected)
            || slots.get(command.sessionId()) != command.slotIndex()) {
            LOG.log(MatchLogEvent.RESULT_SELECTION_IGNORED, sessionId(command.sessionId()), reason("STALE_OR_FOREIGN"));
            return CompletableFuture.completedFuture(null);
        }

        var result = selection.select(command.slotIndex(), command.cardIndex(), random::nextInt);

        if (result.outcome() == MatchCardSelectionOutcome.IGNORED) {
            LOG.log(MatchLogEvent.RESULT_SELECTION_IGNORED, sessionId(command.sessionId()), reason("INVALID_CARD"));
            return CompletableFuture.completedFuture(null);
        }

        boolean occupied = result.outcome() == MatchCardSelectionOutcome.ALREADY_OPEN;
        var notice = new MatchRewardSelectionNotice(result.cardIndex(), result.slotIndex(), occupied);
        LOG.log(
            MatchLogEvent.RESULT_CARD_SELECTED,
            matchId(token.matchId()),
            sessionId(command.sessionId()),
            slotIndex(result.slotIndex()),
            cardIndex(result.cardIndex()),
            reason(occupied ? "OCCUPIED" : result.replay() ? "REPLAY" : "SELECTED")
        );
        var deliveries = outputs.stream().filter(output -> current.test(memberships.get(output.sessionId())))
            .filter(output -> !occupied && !result.replay() || output.sessionId().equals(command.sessionId()))
            .map(output -> deliver(
                output,
                notice
            )).map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(deliveries);
    }

    private CompletionStage<Void> deliver(MatchSubscription recipient, MatchRewardSelectionNotice notice) {
        try {
            return recipient.output().selectReward(notice).handle(LogContext.propagate((ignored, failure) -> {
                if (failure != null) {
                    failed(recipient, failure);
                }

                return null;
            }));
        } catch (RuntimeException failure) {
            failed(recipient, failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void failed(MatchSubscription recipient, Throwable failure) {
        LOG.failure(
            MatchLogEvent.RESULT_SELECTION_FAILED,
            failure,
            matchId(token.matchId()),
            sessionId(recipient.sessionId())
        );
        recipient.output().close("RESULT_SELECTION_DELIVERY_FAILED");
    }
}
