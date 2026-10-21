package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.TestLevels;
import taan.online.character.model.CharacterProgress;
import taan.online.room.model.RoomMode;

class MatchExperience110Test {
    @ParameterizedTest
    @ValueSource(longs = {1, 60000, 300000, 600000, 1800000, 2700000})
    void awardsTheSameExperienceForEveryPositiveDuration(long duration) {
        var original = ExperienceTestFixtures.result();
        var result = new MatchResult(
            original.matchId(),
            original.finishedAt(),
            original.winningTeam(),
            original.cause(),
            original.policyVersion(),
            original.participants(),
            Math.min(duration, 1800000)
        );
        var rules = new MatchExperienceRules(TestLevels.table());
        var winner = rules.calculate(result, result.participants().getFirst(), CharacterProgress.initial());
        var loser = rules.calculate(result, result.participants().getLast(), CharacterProgress.initial());

        assertEquals(1000, winner.baseDelta());
        assertEquals(100, winner.pcroomDelta());
        assertEquals(60, winner.medalDelta());
        assertEquals(1160, winner.delta());
        assertEquals(140, loser.baseDelta());
        assertEquals(14, loser.pcroomDelta());
        assertEquals(154, loser.delta());
    }

    @ParameterizedTest
    @EnumSource(RoomMode.class)
    void usesTheSamePolicyForEveryModeAndDoesNotRewardAnUnstartedMatch(RoomMode mode) {
        try (var fixture = new MatchBootstrapFixture(2, mode)) {
            fixture.start();
            var snapshot = fixture.snapshot();
            var first = snapshot.participants().getFirst();
            var statistics = new MatchCombatStatistics(
                Map.of(first.sessionId(), 400L),
                Map.of(first.sessionId(), 2),
                Map.of(first.sessionId(), 1L)
            );
            var outcome = new MatchOutcome(Set.of(first.team()), false);
            var zero = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                outcome,
                snapshot.participants(),
                statistics,
                Map.of()
            );

            assertEquals(0, zero.rewardDurationMillis());
            assertFalse(zero.outcome().awardsGranted());
            assertTrue(zero.participants().stream().noneMatch(MatchParticipantResult::awardEligible));
            assertTrue(zero.participants().stream().noneMatch(p -> p.forceMedal() || p.killMedal()));

            var completed = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                outcome,
                snapshot.participants(),
                statistics,
                Map.of(),
                ExperienceTestFixtures.participation(first.sessionId())
            );
            var winner = new MatchExperienceRules(TestLevels.table()).calculate(
                completed,
                completed.participants().getFirst(),
                CharacterProgress.initial()
            );

            assertEquals("pvp-rewards-1.1.0", completed.policyVersion());
            assertEquals(1160, winner.delta());
            assertEquals(1, winner.after().progress().forceMedals());
            assertEquals(1, winner.after().progress().killMedals());
        }
    }

    @Test
    void keepsZeroDurationAndCancellationRewardlessEvenWithAnEligibleInput() {
        var original = ExperienceTestFixtures.result();
        var rules = new MatchExperienceRules(TestLevels.table());
        var zero = new MatchResult(
            original.matchId(),
            original.finishedAt(),
            original.winningTeam(),
            original.cause(),
            original.policyVersion(),
            original.participants(),
            0L
        );
        var winner = zero.participants().getFirst();
        var before = new CharacterProgress(900, 4, 5, 6);
        var unchanged = rules.calculate(zero, winner, before);

        assertEquals(before, unchanged.after().progress());
        assertEquals(0, unchanged.delta());

        var member = new MatchParticipantResult(
            winner.characterId(),
            winner.slotIndex(),
            winner.team(),
            winner.damage(),
            true,
            false,
            winner.kills(),
            false,
            winner.statistics(),
            null
        );
        var cancelled = new MatchResult(
            original.matchId(),
            original.finishedAt(),
            Optional.empty(),
            MatchOutcomeCause.TECHNICAL_CANCELLATION,
            original.policyVersion(),
            List.of(member),
            1000L
        );

        assertEquals(before, rules.calculate(cancelled, member, before).after().progress());
    }
}
