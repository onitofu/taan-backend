package taan.online.match.model;

import java.util.Objects;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;

public final class MatchRewardRules {
    private final MatchExperienceRules experience;

    public MatchRewardRules(CharacterLevelTable levels) {
        experience = new MatchExperienceRules(levels);
    }

    public MatchParticipantResult calculate(
        MatchResult result,
        MatchParticipantResult participant,
        CharacterProgress before,
        long goldBefore
    ) {
        boolean eligible = participant.awardEligible() && result.rewardDurationMillis() > 0
            && result.winningTeam().isPresent() && !participant.passive();
        Integer draw = eligible ? Objects.requireNonNull(participant.statistics().rewardDraw()) : null;
        var progress = experience.calculate(result, participant, before, draw);
        var base = ExperienceFraction.of(0, 1);

        if (eligible) {
            boolean won = result.winningTeam().orElseThrow() == participant.team();
            base = ExperienceFraction.of((won ? 32 : 16) * result.rewardDurationMillis(), 60000);
        }

        int medals = (participant.forceMedal() ? 1 : 0) + (participant.killMedal() ? 1 : 0);
        var medal = base.times(medals * 5L, 100);
        boolean experienceCard = draw != null && draw >= 90 && progress.cardDelta() > 0;
        int cardPercent = draw == null || experienceCard ? 0 : draw < 30 ? 5 : draw < 70 ? 10 : draw < 90 ? 15 : 10;
        var card = base.times(cardPercent, 100);
        long baseDelta = base.truncate();
        long medalDelta = base.plus(medal).truncate() - baseDelta;
        long total = base.plus(medal).plus(card).truncate();
        long cardDelta = total - baseDelta - medalDelta;
        var gold = new MatchGoldReward(goldBefore, Math.addExact(goldBefore, total), baseDelta, medalDelta, cardDelta);
        var prize = draw == null ? null : new MatchRewardCard(
            draw,
            experienceCard ? MatchRewardCardKind.EXPERIENCE : MatchRewardCardKind.GOLD,
            experienceCard ? progress.cardDelta() : cardDelta
        );
        return participant.rewarded(progress, gold, prize);
    }
}
