package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchResult;
import taan.online.room.model.RoomTeam;

class JdbcMatchResultRepositoryTest {
    @Test
    void commitsTheWholeResultAndLocksMedalRecipientsInIdentityOrder() {
        var result = MatchResultFixtures.result(9, 2);
        var database = new MatchJdbcStub(result);
        var stored = new JdbcMatchResultRepository(database.source(), 5).settle(result);

        assertTrue(stored.created());
        assertEquals(result, stored.result());
        assertEquals(List.of(2L, 9L), database.recipients);
        assertTrue(database.calls.indexOf("connection.commit") > database.calls.indexOf("medals.executeUpdate"));
        assertEquals("connection.close", database.calls.getLast());
        assertFalse(database.calls.contains("connection.rollback"));
    }

    @Test
    void replaysStoredWinnersAndCancellationWithoutApplyingCountersAgain() {
        var original = MatchResultFixtures.result(1, 2);

        var winners = List.of(Optional.of(RoomTeam.BLUE), Optional.of(RoomTeam.RED), Optional.<RoomTeam>empty());

        for (var winner : winners) {
            var stored = new MatchResult(
                original.matchId(),
                original.finishedAt(),
                winner,
                winner.isEmpty() ? MatchOutcomeCause.TECHNICAL_CANCELLATION : original.cause(),
                original.policyVersion(),
                winner.isEmpty() ? List.of() : original.participants()
            );
            var database = new MatchJdbcStub(stored);
            database.created = false;
            var replayed = new JdbcMatchResultRepository(database.source(), 5).settle(original);

            assertFalse(replayed.created());
            assertEquals(stored, replayed.result());
            assertTrue(database.recipients.isEmpty());
            assertFalse(database.calls.contains("participants.executeBatch"));
        }
    }

    @Test
    void storesATechnicalCancellationWithoutAwardingAnyMedals() {
        var original = MatchResultFixtures.result(1, 2);
        var cancelled = new MatchResult(
            original.matchId(),
            original.finishedAt(),
            Optional.empty(),
            MatchOutcomeCause.TECHNICAL_CANCELLATION,
            original.policyVersion(),
            List.of()
        );
        var database = new MatchJdbcStub(cancelled);
        var stored = new JdbcMatchResultRepository(database.source(), 5).settle(cancelled);

        assertTrue(stored.created());
        assertEquals(cancelled, stored.result());
        assertTrue(database.recipients.isEmpty());
        assertTrue(database.calls.contains("result.setNull"));
    }

    @Test
    void retriesOnlyConnectionAndTransactionFailuresOnce() {
        var result = MatchResultFixtures.result(1, 2);

        for (String state : List.of("08006", "40001")) {
            var database = new MatchJdbcStub(result);
            database.fail("source.getConnection", new SQLException("private-connection-value", state));

            assertTrue(new JdbcMatchResultRepository(database.source(), 5).settle(result).created());
            assertEquals(2, database.calls.stream().filter("source.getConnection"::equals).count());
        }

        var failedDatabase = new MatchJdbcStub(result);
        failedDatabase.fail("source.getConnection", new SQLException("first-private-value", "08006"));
        failedDatabase.fail("source.getConnection", new SQLException("second-private-value", "08006"));
        var failed = assertThrows(
            MatchPersistenceException.class,
            () -> new JdbcMatchResultRepository(failedDatabase.source(), 5).settle(result)
        );

        assertEquals(1, failed.getCause().getSuppressed().length);
        assertFalse(failed.getMessage().contains("private"));

        var database = new MatchJdbcStub(result);
        database.fail("source.getConnection", new SQLException("state-unavailable"));
        var noState = database;

        assertThrows(
            MatchPersistenceException.class,
            () -> new JdbcMatchResultRepository(noState.source(), 5).settle(result)
        );
        assertEquals(1, database.calls.stream().filter("source.getConnection"::equals).count());
    }

    @Test
    void rollsBackPartialWritesAndPreservesTheOriginalFailureWhenRollbackFails() {
        var result = MatchResultFixtures.result(1, 2);

        for (String stage : List.of("result.executeUpdate", "participants.executeBatch", "connection.commit")) {
            var database = new MatchJdbcStub(result);
            database.fail(stage, new SQLException("private-row-value", "XX000"));
            database.fail("connection.rollback", new SQLException("private-rollback-value", "XX000"));
            var failed = assertThrows(
                MatchPersistenceException.class,
                () -> new JdbcMatchResultRepository(database.source(), 5).settle(result)
            );

            assertTrue(database.calls.contains("connection.rollback"));
            assertEquals(1, failed.getCause().getSuppressed().length);
            assertEquals("connection.close", database.calls.getLast());
        }

        var overflow = new MatchJdbcStub(result);
        overflow.counterOverflow = true;

        assertThrows(
            MatchPersistenceException.class,
            () -> new JdbcMatchResultRepository(overflow.source(), 5).settle(result)
        );
        assertTrue(overflow.calls.contains("connection.rollback"));
    }

    @Test
    void rejectsMissingOrCorruptStoredResultsAndRollsBackWithoutCreditingAnything() {
        var result = MatchResultFixtures.result(1, 2);

        for (int mode = 0; mode < 3; mode++) {
            var database = new MatchJdbcStub(result);
            database.created = false;
            database.missingResult = mode == 0;
            database.corruptCause = mode == 1;
            database.storedTeam = mode == 2 ? (short) 3 : null;

            assertThrows(
                RuntimeException.class,
                () -> new JdbcMatchResultRepository(database.source(), 5).settle(result)
            );
            assertTrue(database.calls.contains("connection.rollback"));
            assertTrue(database.recipients.isEmpty());
        }
    }
}
