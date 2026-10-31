package taan.online.match.model;

import taan.online.character.model.CharacterProgress;

public interface MatchRewardPolicy {
    MatchRewardCalculation calculate(
        MatchResult result,
        MatchParticipantResult participant,
        CharacterProgress before,
        int levelBefore,
        long goldBefore
    );
}
