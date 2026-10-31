package taan.online.match.model;

import taan.online.character.model.CharacterLevelTable;

public final class MatchRewardPolicies {
    private final MatchRewardPolicy current;
    private final MatchRewardPolicy legacy;

    public MatchRewardPolicies(CharacterLevelTable levels) {
        current = new Pvp110RewardPolicy(levels);
        legacy = new LegacyMatchRewardPolicy(levels);
    }

    public MatchRewardPolicy select(MatchResult result) {
        return result.rewardDurationMillis() != null && result.policyVersion().equals("pvp-rewards-1.1.0")
            ? current : legacy;
    }
}
