package taan.online.room.model;

import java.util.Objects;
import java.util.UUID;
import taan.online.character.model.GameCharacter;

public record RoomParticipant(UUID sessionId, GameCharacter character, int slotIndex, RoomTeam team, int weaponId) {
    public RoomParticipant {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(character);
        Objects.requireNonNull(team);

        if (slotIndex < 0 || slotIndex > 7 || weaponId < 0 || weaponId > 65535) {
            throw new IllegalArgumentException("Invalid room participant placement");
        }
    }
}
