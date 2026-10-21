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
    long guardAdjustment,
    long cardDelta
) {
    public MatchExperienceReward(
        long experienceBefore,
        int levelBefore,
        CharacterProgressSnapshot after,
        long baseDelta,
        long pcroomDelta,
        long medalDelta,
        long guardAdjustment
    ) {
        this(experienceBefore, levelBefore, after, baseDelta, pcroomDelta, medalDelta, guardAdjustment, 0);
    }

    public MatchExperienceReward {
        Objects.requireNonNull(after);

        if (experienceBefore < 0 || experienceBefore > 1048576 || levelBefore < 1 || levelBefore > after.level()
            || cardDelta < 0 || Math.addExact(
                Math.addExact(
                    Math.addExact(
                        baseDelta,
                        pcroomDelta
                    ),
                    medalDelta
                ),
                cardDelta
            )
                != after.progress().experience() - experienceBefore) {
            throw new IllegalArgumentException("Inconsistent experience reward");
        }
    }

    public long delta() {
        return after.progress().experience() - experienceBefore;
    }
}
