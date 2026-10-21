package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import taan.online.TestLevels;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.room.model.RoomTeam;

class MatchRewardRulesTest {
    @ParameterizedTest
    @CsvFileSource(resources = "/match/rewards/golden.csv", numLinesToSkip = 1)
    void matchesIndependentExperienceGoldAndCardResults(
        long duration,
        long before,
        boolean won,
        long damage,
        int kills,
        long shots,
        long window,
        boolean passed,
        boolean eligible,
        int draw,
        long after,
        int level,
        long base,
        long club,
        long medal,
        long expCard,
        long guard,
        long goldAfter,
        long goldBase,
        long goldMedal,
        long goldCard,
        String kind,
        long amount,
        boolean force,
        boolean kill
    ) {
        var participant = new MatchParticipantResult(
            new CharacterId(1),
            0,
            RoomTeam.BLUE,
            damage,
            eligible,
            force,
            kills,
            kill,
            new MatchParticipantStatistics(1000, shots, window, passed, draw),
            null
        );
        var other = new MatchParticipantResult(
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
            "pvp-rewards-1.1.0",
            List.of(participant, other),
            Math.min(duration, 1800000)
        );
        var reward = new MatchRewardRules(TestLevels.table()).calculate(
            result,
            participant,
            new CharacterProgress(before, 4, 5, 6),
            123
        );
        var exp = reward.experienceReward();
        var gold = reward.goldReward();

        assertEquals(after, exp.after().progress().experience());
        assertEquals(level, exp.after().level());
        assertEquals(base, exp.baseDelta());
        assertEquals(club, exp.pcroomDelta());
        assertEquals(medal, exp.medalDelta());
        assertEquals(expCard, exp.cardDelta());
        assertEquals(guard, exp.guardAdjustment());
        assertEquals(goldAfter, gold.after());
        assertEquals(goldBase, gold.baseDelta());
        assertEquals(goldMedal, gold.medalDelta());
        assertEquals(goldCard, gold.cardDelta());
        assertEquals(goldAfter - 123, gold.delta());
        assertEquals(4 + (force ? 1 : 0), exp.after().progress().forceMedals());
        assertEquals(5 + (kill ? 1 : 0), exp.after().progress().killMedals());
        assertEquals(6, exp.after().progress().supplyMedals());

        if (kind.equals("NONE")) {
            assertNull(reward.card());
        } else {
            assertEquals(draw, reward.card().draw());
            assertEquals(
                kind.equals("EXP") ? MatchRewardCardKind.EXPERIENCE : MatchRewardCardKind.GOLD,
                reward.card().kind()
            );
            assertEquals(amount, reward.card().amount());
        }
    }
}
