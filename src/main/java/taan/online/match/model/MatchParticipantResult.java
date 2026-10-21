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
    boolean forceMedal,
    int kills,
    boolean killMedal,
    MatchParticipantStatistics statistics,
    MatchExperienceReward experienceReward
) {
    public MatchParticipantResult(
        CharacterId characterId,
        int slotIndex,
        RoomTeam team,
        long damage,
        boolean awardEligible,
        boolean forceMedal
    ) {
        this(characterId, slotIndex, team, damage, awardEligible, forceMedal, 0, false, null, null);
    }

    public MatchParticipantResult {
        Objects.requireNonNull(characterId);
        Objects.requireNonNull(team);

        if (slotIndex < 0 || slotIndex > 7 || damage < 0 || damage > 0xffffffffL || kills < 0 || kills > 255
            || forceMedal && (!awardEligible || damage == 0) || killMedal && (!awardEligible || kills < 2)
            || experienceReward != null && !experienceReward.after().characterId().equals(characterId)) {
            throw new IllegalArgumentException("Invalid match participant result");
        }
    }

    public boolean hadOpportunity() {
        return Objects.requireNonNull(statistics).hadOpportunity();
    }

    public boolean passive() {
        return hadOpportunity() && statistics.acceptedShots() == 0 && damage == 0 && kills == 0;
    }

    public MatchParticipantResult rewarded(MatchExperienceReward reward) {
        return new MatchParticipantResult(
            characterId,
            slotIndex,
            team,
            damage,
            awardEligible,
            forceMedal,
            kills,
            killMedal,
            null,
            reward
        );
    }
}
