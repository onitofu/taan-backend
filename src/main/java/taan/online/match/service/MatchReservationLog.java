package taan.online.match.service;

import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.mapId;
import static taan.online.logging.LogField.roomMode;
import static taan.online.logging.LogField.reason;

import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.room.service.model.RoomMatchDraft;

final class MatchReservationLog {
    private static final EventLogger LOG = EventLogger.forClass(MatchReservationLog.class);

    private MatchReservationLog() {
    }

    static void log(MatchLogEvent event, RoomMatchDraft draft, String result) {
        LOG.log(
            event,
            matchId(draft.token().matchId()),
            roomId(draft.token().roomId().value()),
            roomGeneration(draft.token().generation()),
            sessionId(draft.ownerSessionId()),
            channelId(draft.channelId()),
            mapId(draft.settings().mapId()),
            roomMode(draft.settings().mode().name()),
            reason(result)
        );
    }
}
