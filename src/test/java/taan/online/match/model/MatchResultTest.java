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
            var damage = new MatchDamageStatistics(Map.of(first.sessionId(), 400L, last.sessionId(), 400L));
            var outcome = new MatchOutcome(Set.of(first.team()), false);
            var tied = MatchResult.finish(snapshot.id(), Instant.EPOCH, outcome, roster, damage, Map.of());

            assertEquals(2, tied.participants().stream().filter(MatchParticipantResult::forceMedal).count());
            assertTrue(tied.outcome().awardsGranted());
            assertEquals(outcome.winningTeams(), tied.outcome().winningTeams());
            assertEquals("damage-medal-v1", tied.policyVersion());
            assertThrows(UnsupportedOperationException.class, () -> tied.participants().clear());

            for (var exit : MatchExitKind.values()) {
                var result = MatchResult.finish(
                    snapshot.id(),
                    Instant.EPOCH,
                    outcome,
                    roster,
                    damage.credit(first.sessionId(), 300),
                    Map.of(first.sessionId(), exit)
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
                Map.of()
            );
            assertTrue(cancelled.winningTeam().isEmpty());
            assertFalse(cancelled.outcome().awardsGranted());
            assertTrue(cancelled.participants().stream().noneMatch(MatchParticipantResult::awardEligible));

            var zero = MatchResult.finish(
                snapshot.id(),
                Instant.EPOCH,
                outcome,
                roster,
                MatchDamageStatistics.empty(),
                Map.of()
            );
            assertFalse(zero.outcome().awardsGranted());
        }
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
