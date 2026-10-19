package taan.online.match.service.model;

import java.util.UUID;
import taan.online.match.model.MatchId;
import taan.online.room.model.RoomId;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

public record MatchCommandContext(
    MatchId matchId,
    RoomId roomId,
    UUID roomGeneration,
    int channelId,
    UUID sessionId,
    UUID admissionId
) {
    public static MatchCommandContext from(RoomMatchContext context) {
        var member = context.membership();
        return new MatchCommandContext(
            new MatchId(context.token().matchId()),
            member.roomId(),
            member.generation(),
            member.channelId(),
            member.sessionId(),
            member.admissionId()
        );
    }

    public RoomMatchToken token() {
        return new RoomMatchToken(roomId, roomGeneration, matchId.value());
    }

    public RoomMatchContext roomContext() {
        return new RoomMatchContext(
            new RoomMembership(sessionId, roomId, roomGeneration, channelId, admissionId),
            token()
        );
    }
}
