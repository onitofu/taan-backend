package taan.online.room.service.model;

import java.util.UUID;
import taan.online.character.model.CharacterProgressSnapshot;

public record RoomMatchCharacterProgress(
    UUID sessionId,
    UUID admissionId,
    CharacterProgressSnapshot progress,
    Long goldAfter
) {
    public RoomMatchCharacterProgress(UUID sessionId, UUID admissionId, CharacterProgressSnapshot progress) {
        this(sessionId, admissionId, progress, null);
    }
}
