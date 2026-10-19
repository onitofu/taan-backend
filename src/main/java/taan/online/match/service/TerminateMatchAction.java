package taan.online.match.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationResult;

public final class TerminateMatchAction {
    private static final EventLogger LOG = EventLogger.forClass(TerminateMatchAction.class);

    private final MatchRegistry matches;

    public TerminateMatchAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public CompletionStage<RoomMatchTerminationResult> execute(UUID id, RoomMatchContext context) {
        return execute(id, context, RoomMatchEndReason.GIVE_UP);
    }

    public CompletionStage<RoomMatchTerminationResult> execute(
        UUID id,
        RoomMatchContext context,
        RoomMatchEndReason reason
    ) {
        var result = context != null && context.membership().sessionId().equals(id)
            ? matches.terminate(context, reason) : CompletableFuture.completedFuture(
                RoomMatchTerminationResult.ignored()
            );
        return result.thenApply(
            value -> {
                if (value.termination() == null) {
                    LOG.log(MatchLogEvent.TERMINATION_IGNORED, sessionId(id), reason("STALE_CONTEXT"));
                }

                return value;
            }
        );
    }
}
