package taan.online.room.service;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.UUID;
import taan.online.logging.EventLogger;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomTeamResult;

public final class ChangeRoomTeamAction {
    private static final EventLogger LOG = EventLogger.forClass(ChangeRoomTeamAction.class);

    private final RoomRegistry rooms;

    public ChangeRoomTeamAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public ChangeRoomTeamResult execute(UUID id, RoomTeam desiredTeam) {
        var result = rooms.changeTeam(id, desiredTeam);

        if (result.outcome() == ChangeRoomTeamOutcome.REJECTED
            || result.outcome() == ChangeRoomTeamOutcome.NO_OPEN_SLOT) {
            LOG.log(RoomLogEvent.TEAM_CHANGE_REJECTED, sessionId(id), reason(result.reason()));
        }

        return result;
    }
}
