package taan.online.match.model;

import java.util.Objects;
import java.util.UUID;
import taan.online.character.model.GameCharacter;
import taan.online.room.model.RoomTeam;
import taan.online.room.model.RoomSlotState;

public record MatchParticipant(
    UUID sessionId,
    UUID admissionId,
    int slotIndex,
    RoomTeam team,
    GameCharacter character,
    int weaponId,
    MapSpawnDefinition spawn,
    int facing,
    int hp,
    int maxHp,
    int energy,
    int maxEnergy,
    RoomSlotState roomSlotState
) {
    public MatchParticipant {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(admissionId);
        Objects.requireNonNull(team);
        Objects.requireNonNull(character);
        Objects.requireNonNull(spawn);
        Objects.requireNonNull(roomSlotState);

        if (!roomSlotState.occupied() || slotIndex < 0 || slotIndex > 7
            || ((slotIndex & 2) == 0) != (team == RoomTeam.BLUE)
            || weaponId != 0 || !spawn.valid() || spawn.side() != 0 && spawn.side() != (team == RoomTeam.BLUE ? 1 : 2)
            || facing < 0 || facing > 1 || hp < 1 || hp > maxHp || energy < 0 || energy > maxEnergy) {
            throw new IllegalArgumentException("Invalid match participant");
        }
    }
}
