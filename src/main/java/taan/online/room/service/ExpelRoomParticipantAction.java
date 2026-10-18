package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.ExpelRoomParticipantResult;

public final class ExpelRoomParticipantAction {
    private final RoomRegistry rooms;

    public ExpelRoomParticipantAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public ExpelRoomParticipantResult execute(UUID sessionId, long targetCharacterId) {
        return rooms.expel(sessionId, targetCharacterId);
    }
}
