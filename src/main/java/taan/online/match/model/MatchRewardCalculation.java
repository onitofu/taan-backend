package taan.online.match.model;

import taan.online.character.model.CharacterProgress;

public record MatchRewardCalculation(
    MatchParticipantResult participant,
    CharacterProgress progress,
    int level,
    long gold
) {
}
