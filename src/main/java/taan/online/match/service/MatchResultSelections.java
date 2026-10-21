package taan.online.match.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import taan.online.logging.EventLogger;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchResultReceipt;
import taan.online.match.service.model.SelectMatchRewardCommand;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

final class MatchResultSelections {
    private static final EventLogger LOG = EventLogger.forClass(MatchResultSelections.class);
    private final Map<UUID, MatchResultReceipt> receipts = new ConcurrentHashMap<>();
    private final Map<RoomMatchToken, MatchResultSelectionRuntime> results = new ConcurrentHashMap<>();
    private final MatchCommandExecutor executor;
    private final int capacity;
    private final RandomGenerator random;
    private final WarningLogLimiter warnings;

    MatchResultSelections(
        MatchCommandExecutor executor,
        int capacity,
        RandomGenerator random,
        WarningLogLimiter warnings
    ) {
        this.executor = executor;
        this.capacity = capacity;
        this.random = random;
        this.warnings = warnings;
    }

    void reserve(RoomMatchToken token, List<RoomMembership> members) {
        members.forEach(member -> receipts.put(member.sessionId(), new MatchResultReceipt(token, member)));
    }

    MatchResultReceipt receipt(UUID session) {
        return receipts.get(session);
    }

    void publish(RoomMatchTermination terminal, List<MatchParticipant> participants, List<MatchSubscription> outputs) {
        var members = terminal.recipients().stream().filter(m -> {
            var receipt = receipts.get(m.sessionId());
            return receipt != null && receipt.token().equals(terminal.token())
                && receipt.membership().equals(m);
        }).toList();

        if (members.isEmpty()) {
            return;
        }

        results.put(
            terminal.token(),
            new MatchResultSelectionRuntime(
                terminal.token(),
                members,
                participants,
                outputs.stream().filter(o -> members.stream()
                .anyMatch(m -> m.sessionId().equals(o.sessionId()))).toList(),
                m -> {
                    var receipt = receipts.get(m.sessionId());
                    return receipt != null && receipt.token().equals(terminal.token())
                        && receipt.membership().equals(m);
                },
                executor,
                capacity,
                random,
                warnings
            )
        );
        clean();
    }

    CompletionStage<Void> select(SelectMatchRewardCommand command) {
        var runtime = command.context() == null ? null : results.get(command.context().token());
        if (runtime == null) {
            LOG.log(MatchLogEvent.RESULT_SELECTION_IGNORED, sessionId(command.sessionId()), reason("STALE_CONTEXT"));
            return CompletableFuture.completedFuture(null);
        }

        return runtime.select(command);
    }

    void release(RoomMembership membership) {
        receipts.computeIfPresent(
            membership.sessionId(),
            (id, receipt) -> {
                return receipt.membership().equals(membership) ? null : receipt;
            }
        );
        clean();
    }

    void remove(UUID session) {
        receipts.remove(session);
        clean();
    }

    void cancel(RoomMatchToken token) {
        receipts.values().removeIf(receipt -> receipt.token().equals(token));
        clean();
    }

    private void clean() {
        results.entrySet().removeIf(entry -> {
            if (receipts.values().stream().noneMatch(receipt -> receipt.token().equals(entry.getKey()))) {
                entry.getValue().close();
                return true;
            }

            return false;
        });
    }
}
