package taan.online.match.model;

import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;

public final class Pvp110RewardPolicy implements MatchRewardPolicy {
    private final MatchRewardRules rules;

    public Pvp110RewardPolicy(CharacterLevelTable levels) {
        rules = new MatchRewardRules(levels);
    }

    @Override
    public MatchRewardCalculation calculate(
        MatchResult result,
        MatchParticipantResult participant,
        CharacterProgress before,
        int levelBefore,
        long goldBefore
    ) {
        var settled = rules.calculate(result, participant, before, goldBefore);
        var after = settled.experienceReward().after();
        return new MatchRewardCalculation(settled, after.progress(), after.level(), settled.goldReward().after());
    }
}
