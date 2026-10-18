package taan.online.room.service;

import java.util.UUID;
import taan.online.room.service.model.SetCaptainCandidateResult;

public final class SetCaptainCandidateAction {
    private final RoomRegistry rooms;

    public SetCaptainCandidateAction(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public SetCaptainCandidateResult execute(UUID id, long characterId, boolean candidate) {
        return rooms.setCaptainCandidate(id, characterId, candidate);
    }
}
