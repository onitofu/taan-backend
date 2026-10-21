package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.ExperienceTestFixtures;
import taan.online.MatchBootstrapFixture;
import taan.online.character.model.CharacterId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomTeam;

class MatchResultTest {
    @Test
    void grantsEveryPositiveLeaderAcrossTeamsAndExcludesDeparturesWithoutDiscardingTheirDamage() {
        try (var fixture = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            fixture.start();
            var snapshot = fixture.snapshot();
            var roster = snapshot.participants();
            var first = roster.getFirst();
            var last = roster.getLast();
            var damage = new MatchCombatStatistics(Map.of(first.sessionId(), 400L, last.sessionId(), 400L));
            var outcome = new MatchOutcome(Set.of(first.team()), false);
            var tied = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                outcome,
                roster,
                damage,
                Map.of(),
                ExperienceTestFixtures.participation(roster.getFirst().sessionId())
            );

            assertEquals(2, tied.participants().stream().filter(MatchParticipantResult::forceMedal).count());
            assertTrue(tied.outcome().awardsGranted());
            assertEquals(outcome.winningTeams(), tied.outcome().winningTeams());
            assertEquals("pvp-rewards-1.1.0", tied.policyVersion());
            assertThrows(UnsupportedOperationException.class, () -> tied.participants().clear());

            for (var exit : MatchExitKind.values()) {
                var result = MatchResult.finish(
                    snapshot.id(),
                    Instant.EPOCH,
                    outcome,
                    roster,
                    damage.credit(first.sessionId(), 300),
                    Map.of(first.sessionId(), exit),
                    ExperienceTestFixtures.participation(roster.getFirst().sessionId())
                );
                var departed = result.participants().getFirst();

                assertFalse(departed.awardEligible());
                assertFalse(departed.forceMedal());
                assertEquals(700, departed.damage());
                assertTrue(result.participants().getLast().forceMedal());
            }

            var cancelled = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                MatchOutcome.previewAborted(),
                roster,
                damage,
                Map.of(),
                ExperienceTestFixtures.participation(roster.getFirst().sessionId())
            );
            assertTrue(cancelled.winningTeam().isEmpty());
            assertFalse(cancelled.outcome().awardsGranted());
            assertTrue(cancelled.participants().stream().noneMatch(MatchParticipantResult::awardEligible));

            var zero = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                outcome,
                roster,
                MatchCombatStatistics.empty(),
                Map.of(),
                ExperienceTestFixtures.participation(roster.getFirst().sessionId())
            );
            assertFalse(zero.outcome().awardsGranted());
        }
    }

    @Test
    void appliesTheKillThresholdAndDistinguishesPassivityFromDamageAndKillsWithoutShotReports() {
        try (var f = new MatchBootstrapFixture(4, RoomMode.ITEM)) {
            f.start();
            var snapshot = f.snapshot();
            var first = snapshot.participants().getFirst();
            var participation = MatchParticipationState.initial().start(first.sessionId(), 0).stop(6000000000L);
            var stats = new MatchCombatStatistics(Map.of(), Map.of(first.sessionId(), 2), Map.of());
            var result = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                new MatchOutcome(Set.of(first.team()), false),
                snapshot.participants(),
                stats,
                Map.of(),
                participation
            );
            var member = result.participants().getFirst();

            assertFalse(member.passive());
            assertTrue(member.killMedal());
            assertFalse(member.forceMedal());
            assertTrue(result.outcome().awardsGranted());
            assertFalse(new MatchParticipantResult(
                member.characterId(),
                member.slotIndex(),
                member.team(),
                10,
                true,
                false,
                0,
                false,
                new MatchParticipantStatistics(1000, 0, 6000, false),
                null
            ).passive());
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchResult(
                    result.matchId(),
                    result.finishedAt(),
                    Optional.empty(),
                    MatchOutcomeCause.TECHNICAL_CANCELLATION,
                    result.policyVersion(),
                    List.of(member),
                    result.rewardDurationMillis()
                )
            );
            var passive = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                new MatchOutcome(Set.of(first.team()), false),
                snapshot.participants(),
                MatchCombatStatistics.empty(),
                Map.of(),
                participation
            );
            assertTrue(passive.participants().getFirst().passive());
            assertFalse(passive.participants().getFirst().killMedal());
        }
    }

    @Test
    void reportsPositiveExperienceAsAnAwardEvenWithoutMedalsAndDoesNotReportACappedZeroPayout() {
        var original = ExperienceTestFixtures.result();
        var members = original.participants().stream().map(p -> new MatchParticipantResult(
            p.characterId(),
            p.slotIndex(),
            p.team(),
            p.damage(),
            p.awardEligible(),
            false,
            p.kills(),
            false,
            p.statistics(),
            null
        )).toList();
        var result = new MatchResult(
            original.matchId(),
            original.finishedAt(),
            original.winningTeam(),
            original.cause(),
            original.policyVersion(),
            members,
            original.rewardDurationMillis()
        );

        assertTrue(ExperienceTestFixtures.settle(result, 0).outcome().awardsGranted());
        assertFalse(ExperienceTestFixtures.settle(result, 1048576).outcome().awardsGranted());
    }

    @Test
    void rejectsInvalidParticipantCountersAndContradictoryDurableResults() {
        var id = new CharacterId(1);
        var participant = new MatchParticipantResult(id, 0, RoomTeam.BLUE, 10, true, true);

        for (int slot : new int[]{-1, 8}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchParticipantResult(id, slot, RoomTeam.BLUE, 10, true, true)
            );
        }

        for (long damage : new long[]{-1, 0x100000000L}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchParticipantResult(id, 0, RoomTeam.BLUE, damage, false, false)
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(id, 0, RoomTeam.BLUE, 0, true, true)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchParticipantResult(id, 0, RoomTeam.BLUE, 1, false, true)
        );
        assertEquals(0xffffffffL, new MatchParticipantResult(id, 7, RoomTeam.RED, 0xffffffffL, false, false).damage());

        var match = new MatchId(UUID.randomUUID());
        var winner = Optional.of(RoomTeam.BLUE);
        var cause = MatchOutcomeCause.ELIMINATION;
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchResult(match, Instant.EPOCH, Optional.empty(), cause, "v1", List.of())
        );

        for (String version : List.of(" ", "x".repeat(33))) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchResult(match, Instant.EPOCH, winner, cause, version, List.of())
            );
        }

        var duplicate = new ArrayList<>(List.of(participant, participant));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchResult(match, Instant.EPOCH, winner, cause, "v1", duplicate)
        );
        duplicate.set(1, new MatchParticipantResult(new CharacterId(2), 0, RoomTeam.BLUE, 0, true, false));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchResult(match, Instant.EPOCH, winner, cause, "v1", duplicate)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchResult(match, Instant.EPOCH, winner, cause, "v1", Collections.nCopies(9, participant))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchResult(
                match,
                Instant.EPOCH,
                Optional.empty(),
                MatchOutcomeCause.TECHNICAL_CANCELLATION,
                "v1",
                List.of(participant)
            )
        );
    }
}
