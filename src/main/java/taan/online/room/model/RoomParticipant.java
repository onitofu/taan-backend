package taan.online.room.model;

import java.util.Objects;
import java.util.UUID;
import taan.online.character.model.GameCharacter;

public record RoomParticipant(UUID sessionId, GameCharacter character, int weaponId) {
    public RoomParticipant {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(character);

        if (weaponId < 0 || weaponId > 65535) {
            throw new IllegalArgumentException("Invalid room participant weapon");
        }
    }
}
