package taan.online.match.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.room.service.model.RoomMatchContext;

public final class ConsumeMatchResultAction {
    private static final EventLogger LOG = EventLogger.forClass(ConsumeMatchResultAction.class);

    private final MatchRegistry matches;

    public ConsumeMatchResultAction(MatchRegistry matches) {
        this.matches = matches;
    }

    public boolean execute(UUID id, RoomMatchContext context) {
        boolean accepted = context != null && context.membership().sessionId().equals(id)
            && matches.consumeResult(context);
        LOG.log(
            MatchLogEvent.RESULT_SELECTION_IGNORED,
            sessionId(id),
            reason(accepted ? "NO_AWARDS" : "STALE_CONTEXT")
        );
        return accepted;
    }
}
