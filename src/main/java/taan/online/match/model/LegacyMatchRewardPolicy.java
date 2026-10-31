package taan.online.match.model;

import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;

public final class LegacyMatchRewardPolicy implements MatchRewardPolicy {
    private final MatchExperienceRules rules;

    public LegacyMatchRewardPolicy(CharacterLevelTable levels) {
        rules = new MatchExperienceRules(levels);
    }

    @Override
    public MatchRewardCalculation calculate(
        MatchResult result,
        MatchParticipantResult participant,
        CharacterProgress before,
        int levelBefore,
        long goldBefore
    ) {
        if (result.rewardDurationMillis() == null) {
            if (participant.forceMedal() && before.forceMedals() == 0xffffffffL) {
                throw new MatchMedalLimitException();
            }

            var progress = new CharacterProgress(
                before.experience(),
                Math.addExact(before.forceMedals(), participant.forceMedal() ? 1 : 0),
                before.killMedals(),
                before.supplyMedals()
            );
            return new MatchRewardCalculation(participant, progress, levelBefore, goldBefore);
        }

        var settled = participant.rewarded(rules.calculate(result, participant, before));
        var after = settled.experienceReward().after();
        return new MatchRewardCalculation(settled, after.progress(), after.level(), goldBefore);
    }
}
