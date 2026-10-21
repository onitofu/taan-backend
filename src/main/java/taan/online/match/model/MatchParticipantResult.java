package taan.online.match.model;

import java.util.Objects;
import taan.online.character.model.CharacterId;
import taan.online.room.model.RoomTeam;

public record MatchParticipantResult(
    CharacterId characterId,
    int slotIndex,
    RoomTeam team,
    long damage,
    boolean awardEligible,
    boolean forceMedal
) {
    public MatchParticipantResult {
        Objects.requireNonNull(characterId);
        Objects.requireNonNull(team);

        if (slotIndex < 0 || slotIndex > 7 || damage < 0 || damage > 0xffffffffL
            || forceMedal && (!awardEligible || damage == 0)) {
            throw new IllegalArgumentException("Invalid match participant result");
        }
    }
}
