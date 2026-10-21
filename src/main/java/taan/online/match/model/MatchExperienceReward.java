package taan.online.match.model;

import java.util.Objects;
import taan.online.character.model.CharacterProgressSnapshot;

public record MatchExperienceReward(
    long experienceBefore,
    int levelBefore,
    CharacterProgressSnapshot after,
    long baseDelta,
    long pcroomDelta,
    long medalDelta,
    long guardAdjustment
) {
    public MatchExperienceReward {
        Objects.requireNonNull(after);

        if (experienceBefore < 0 || experienceBefore > 1048576 || levelBefore < 1 || levelBefore > after.level()
            || Math.addExact(Math.addExact(baseDelta, pcroomDelta), medalDelta)
                != after.progress().experience() - experienceBefore) {
            throw new IllegalArgumentException("Inconsistent experience reward");
        }
    }

    public long delta() {
        return after.progress().experience() - experienceBefore;
    }
}
