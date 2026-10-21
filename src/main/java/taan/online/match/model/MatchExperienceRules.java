package taan.online.match.model;

import java.util.Objects;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;

public final class MatchExperienceRules {
    private final CharacterLevelTable levels;

    public MatchExperienceRules(CharacterLevelTable levels) {
        this.levels = Objects.requireNonNull(levels);
    }

    public MatchExperienceReward calculate(MatchResult match, MatchParticipantResult member, CharacterProgress before) {
        int level = levels.level(before.experience());
        boolean eligible = member.awardEligible() && match.rewardDurationMillis() > 0
            && match.winningTeam().isPresent();
        boolean passive = member.passive();
        var base = ExperienceFraction.of(0, 1);

        if (eligible) {
            var winnerBase = ExperienceFraction.of(1000, 1);
            boolean won = match.winningTeam().orElseThrow() == member.team();

            if (passive) {
                base = !won && level < 71 ? winnerBase.times(-1, 4) : base;
            } else if (won) {
                base = winnerBase;
            } else {
                long enemyHp = match.participants().stream().filter(p -> p.team() != member.team())
                    .mapToLong(p -> p.statistics().maxHp()).sum();
                long allies = match.participants().stream().filter(p -> p.team() == member.team()).count();
                long denominator = Math.multiplyExact(enemyHp, 4);
                long contribution = Math.min(
                    denominator,
                    Math.addExact(Math.multiplyExact(member.damage(), allies * 4), member.kills() * enemyHp)
                );
                long factor = Math.addExact(denominator * 5, contribution * 45);

                if (!member.hadOpportunity()) {
                    factor = Math.max(factor, denominator * 25);
                }

                base = winnerBase.times(factor, denominator * 100);
            }
        }

        var club = eligible && !passive ? base.times(1, 10) : ExperienceFraction.of(0, 1);
        boolean forceMedal = eligible && member.forceMedal();
        boolean killMedal = eligible && member.killMedal();
        int medals = (forceMedal ? 1 : 0) + (killMedal ? 1 : 0);
        var medal = base.times(medals * 3L, 100);
        long baseDelta = base.truncate();
        long clubDelta = base.plus(club).truncate() - baseDelta;
        long medalDelta = base.plus(club).plus(medal).truncate() - baseDelta - clubDelta;
        long proposed = baseDelta + clubDelta + medalDelta;
        long after = Math.clamp(
            before.experience() + proposed,
            levels.threshold(level),
            CharacterLevelTable.EXPERIENCE_CAP
        );
        long adjustment = after - before.experience() - proposed;

        if (adjustment > 0) {
            baseDelta += adjustment;
        } else {
            long remaining = -adjustment;
            long removed = Math.min(remaining, Math.max(0, medalDelta));
            medalDelta -= removed;
            remaining -= removed;
            removed = Math.min(remaining, Math.max(0, clubDelta));
            clubDelta -= removed;
            remaining -= removed;
            baseDelta -= remaining;
        }

        var progress = new CharacterProgress(
            after,
            before.forceMedals() + (forceMedal ? 1 : 0),
            before.killMedals() + (killMedal ? 1 : 0),
            before.supplyMedals()
        );
        return new MatchExperienceReward(
            before.experience(),
            level,
            new CharacterProgressSnapshot(member.characterId(), levels.level(after), progress),
            baseDelta,
            clubDelta,
            medalDelta,
            adjustment
        );
    }
}
