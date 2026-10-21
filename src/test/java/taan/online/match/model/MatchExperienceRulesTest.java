package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import taan.online.ExperienceTestFixtures;
import taan.online.TestLevels;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.room.model.RoomTeam;

class MatchExperienceRulesTest {
    @ParameterizedTest
    @CsvFileSource(resources = "/match/experience/golden.csv", numLinesToSkip = 1)
    void matchesTheIndependentResearchCalculatorWithoutCardsOrAssists(
        long duration,
        long before,
        boolean won,
        long damage,
        int kills,
        long shots,
        long window,
        boolean passed,
        boolean eligible,
        long after,
        int level,
        boolean forceMedal,
        boolean killMedal,
        long base,
        long club,
        long medal,
        long guard
    ) {
        var member = new MatchParticipantResult(
            new CharacterId(1),
            0,
            RoomTeam.BLUE,
            damage,
            eligible,
            forceMedal,
            kills,
            killMedal,
            new MatchParticipantStatistics(1000, shots, window, passed),
            null
        );
        var opponent = new MatchParticipantResult(
            new CharacterId(2),
            2,
            RoomTeam.RED,
            500,
            true,
            false,
            0,
            false,
            new MatchParticipantStatistics(1000, 1, 1000, false),
            null
        );
        var result = new MatchResult(
            new MatchId(UUID.randomUUID()),
            Instant.EPOCH,
            Optional.of(won ? RoomTeam.BLUE : RoomTeam.RED),
            MatchOutcomeCause.ELIMINATION,
            "pvp-exp-1.1.0",
            List.of(member, opponent),
            duration
        );

        var reward = new MatchExperienceRules(TestLevels.table()).calculate(
            result,
            member,
            new CharacterProgress(before, 5, 6, 7)
        );

        assertEquals(before, reward.experienceBefore());
        assertEquals(after, reward.after().progress().experience());
        assertEquals(level, reward.after().level());
        assertEquals(base, reward.baseDelta());
        assertEquals(club, reward.pcroomDelta());
        assertEquals(medal, reward.medalDelta());
        assertEquals(guard, reward.guardAdjustment());
        assertEquals(after - before, reward.delta());
        assertEquals(5 + (forceMedal ? 1 : 0), reward.after().progress().forceMedals());
        assertEquals(6 + (killMedal ? 1 : 0), reward.after().progress().killMedals());
        assertEquals(7, reward.after().progress().supplyMedals());
        assertEquals(member.characterId(), member.rewarded(reward).experienceReward().after().characterId());
    }

    @Test
    void rejectsCorruptResultFactsAndKeepsTechnicalCancellationRewardless() {
        var input = ExperienceTestFixtures.result();
        var first = input.participants().getFirst();
        var rewarded = ExperienceTestFixtures.settle(input, 0).participants().getFirst().experienceReward();

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(
                first.characterId(),
                0,
                first.team(),
                1,
                true,
                false,
                -1,
                false,
                null,
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(
                first.characterId(),
                0,
                first.team(),
                1,
                true,
                false,
                256,
                false,
                null,
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(
                first.characterId(),
                0,
                first.team(),
                1,
                false,
                false,
                2,
                true,
                null,
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(
                first.characterId(),
                0,
                first.team(),
                1,
                true,
                false,
                1,
                true,
                null,
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(
                new CharacterId(999),
                0,
                first.team(),
                1,
                true,
                false,
                0,
                false,
                null,
                rewarded
            )
        );

        var cancelledMembers = input.participants().stream().map(p -> new MatchParticipantResult(
            p.characterId(),
            p.slotIndex(),
            p.team(),
            p.damage(),
            false,
            false,
            p.kills(),
            false,
            p.statistics(),
            null
        )).toList();
        var cancelled = new MatchResult(
            input.matchId(),
            input.finishedAt(),
            Optional.empty(),
            MatchOutcomeCause.TECHNICAL_CANCELLATION,
            input.policyVersion(),
            cancelledMembers,
            60000L
        );
        var noReward = new MatchExperienceRules(TestLevels.table()).calculate(
            cancelled,
            cancelledMembers.getFirst(),
            new CharacterProgress(700, 1, 2, 3)
        );
        assertEquals(700, noReward.after().progress().experience());
        assertEquals(0, noReward.delta());

        for (long invalid : new long[]{-1, 1800001}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchResult(
                    input.matchId(),
                    input.finishedAt(),
                    input.winningTeam(),
                    input.cause(),
                    input.policyVersion(),
                    input.participants(),
                    invalid
                )
            );
        }
    }

    @Test
    void rejectsInvalidFractionsRewardsStatisticsAndProgressSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> ExperienceFraction.of(1, 0));
        assertThrows(IllegalArgumentException.class, () -> ExperienceFraction.of(1, -1));
        assertThrows(
            ArithmeticException.class,
            () -> new ExperienceFraction(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
                BigInteger.ONE
            ).truncate()
        );
        var after = new CharacterProgressSnapshot(new CharacterId(1), 1, CharacterProgress.initial());

        assertThrows(IllegalArgumentException.class, () -> new MatchExperienceReward(-1, 1, after, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchExperienceReward(1048577, 1, after, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchExperienceReward(0, 0, after, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchExperienceReward(0, 2, after, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MatchExperienceReward(0, 1, after, 1, 0, 0, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterProgressSnapshot(
                new CharacterId(1),
                0,
                CharacterProgress.initial()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new CharacterProgressSnapshot(
                new CharacterId(1),
                110,
                CharacterProgress.initial()
            )
        );
        assertThrows(IllegalArgumentException.class, () -> new MatchParticipantStatistics(0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new MatchParticipantStatistics(1, -1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new MatchParticipantStatistics(1, 0, -1, false));
    }
}
