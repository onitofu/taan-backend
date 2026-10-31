package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.TestLevels;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchParticipantStatistics;
import taan.online.match.model.MatchResult;

class MatchExperienceJdbcTest {
    @Test
    void locksCharactersInIdentityOrderAndWritesExperienceLevelAndBothMedalsBeforeCommit() {
        var input = result(9, 2);
        var database = new MatchJdbcStub(input);

        var stored = new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(input);

        assertTrue(stored.created());
        assertEquals(List.of(2L, 9L), database.lockedCharacters);
        assertEquals(List.of(2060L, 2L, 1L, 1L), database.progressWrites.get(9L));
        assertTrue(database.calls.indexOf("connection.commit") > database.calls.indexOf("participants.executeBatch"));
        assertFalse(database.calls.contains("connection.rollback"));
        assertTrue(stored.result().participants().stream().allMatch(p -> p.experienceReward() != null));

        var replay = new MatchJdbcStub(stored.result());
        replay.created = false;
        var loaded = new RewardTestRepository(replay.source(), 5, TestLevels.table()).settle(input);

        assertEquals(stored.result(), loaded.result());
        assertFalse(loaded.created());
        assertTrue(replay.lockedCharacters.isEmpty());
        assertTrue(replay.progressWrites.isEmpty());
    }

    @Test
    void replaysTheOriginalVersionAndAmountsWhenTheSameMatchIsSubmittedUnderTheNewPolicy() {
        var historical = MatchResultFixtures.historicalExperience(1, 2);
        var proposal = new MatchResult(
            historical.matchId(),
            historical.finishedAt(),
            historical.winningTeam(),
            historical.cause(),
            "pvp-exp-1.1.0",
            result(1, 2).participants(),
            1800000L
        );
        var database = new MatchJdbcStub(historical);
        database.created = false;
        var replayed = new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(proposal);

        assertFalse(replayed.created());
        assertEquals(historical, replayed.result());
        assertEquals("pvp-exp-1.0.2", replayed.result().policyVersion());
        assertEquals(116, replayed.result().participants().getFirst().experienceReward().delta());
        assertEquals(52, replayed.result().participants().getLast().experienceReward().delta());
        assertTrue(database.lockedCharacters.isEmpty());
        assertTrue(database.progressWrites.isEmpty());
    }

    @Test
    void rollsBackMissingCharactersFailedUpdatesAndTransientFailures() {
        var input = result(1, 2);
        var missing = new MatchJdbcStub(input);
        missing.missingCharacter = true;
        assertThrows(
            MatchPersistenceException.class,
            () -> new RewardTestRepository(
                missing.source(),
                5,
                TestLevels.table()
            ).settle(input)
        );
        assertTrue(missing.calls.contains("connection.rollback"));
        var failed = new MatchJdbcStub(input);
        failed.counterOverflow = true;
        assertThrows(
            MatchPersistenceException.class,
            () -> new RewardTestRepository(
                failed.source(),
                5,
                TestLevels.table()
            ).settle(input)
        );
        assertTrue(failed.calls.contains("connection.rollback"));

        for (String stage : List.of("locked.executeQuery", "locked.rows.next", "progress.executeUpdate")) {
            var transientFailure = new MatchJdbcStub(input);
            transientFailure.fail(stage, new SQLException("Private value", "40001"));
            assertTrue(new RewardTestRepository(
                transientFailure.source(),
                5,
                TestLevels.table()
            ).settle(input).created());
            assertTrue(transientFailure.calls.contains("connection.rollback"));
        }
    }

    static MatchResult result(long winner, long loser) {
        var legacy = MatchResultFixtures.result(winner, loser);
        var participants = legacy.participants().stream().map(p -> new MatchParticipantResult(
            p.characterId(),
            p.slotIndex(),
            p.team(),
            p.damage(),
            true,
            p.forceMedal(),
            2,
            true,
            new MatchParticipantStatistics(1000, 1, 6000, false),
            null
        )).toList();
        return new MatchResult(
            legacy.matchId(),
            legacy.finishedAt(),
            legacy.winningTeam(),
            legacy.cause(),
            "pvp-exp-1.1.0",
            participants,
            60000L
        );
    }
}
