package taan.online.match.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.room.service.RoomMatchAccess;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTerminationResult;

public final class TerminateMatchAction {
    private static final EventLogger LOG = EventLogger.forClass(TerminateMatchAction.class);

    private final RoomMatchAccess rooms;

    public TerminateMatchAction(RoomMatchAccess rooms) {
        this.rooms = rooms;
    }

    public RoomMatchTerminationResult execute(UUID id, RoomMatchContext context) {
        var result = context != null && context.membership().sessionId().equals(id)
            ? rooms.terminateMatch(context, RoomMatchEndReason.GIVE_UP) : RoomMatchTerminationResult.ignored();

        if (result.termination() == null) {
            LOG.log(MatchLogEvent.TERMINATION_IGNORED, sessionId(id), reason("STALE_CONTEXT"));
        }

        return result;
    }
}
