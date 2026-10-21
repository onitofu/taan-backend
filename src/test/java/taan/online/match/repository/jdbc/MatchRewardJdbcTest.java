package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.RewardTestFixtures;
import taan.online.TestLevels;

class MatchRewardJdbcTest {
    @Test
    void atomicallyWritesAbsoluteGoldAndExperienceAndReplaysTheSavedPrizeAndShotCounts() {
        for (int draw : new int[]{0, 99}) {
            var result = RewardTestFixtures.result(9, 2, draw);
            var database = new MatchJdbcStub(result);
            database.gold = 123;
            var stored = new JdbcMatchResultRepository(database.source(), 5, TestLevels.table()).settle(result);
            var winner = stored.result().participants().getFirst();

            assertEquals(List.of(2L, 9L), database.lockedCharacters);
            assertEquals(draw == 0 ? 159L : 158L, database.goldWrites.get(9L));
            assertEquals(draw == 0 ? 2060L : 2260L, database.progressWrites.get(9L).getFirst());
            assertEquals(draw, winner.card().draw());
            assertEquals(0.5f, winner.shots().accuracy());
            var replay = new MatchJdbcStub(stored.result());
            replay.created = false;
            var loaded = new JdbcMatchResultRepository(replay.source(), 5, TestLevels.table()).settle(result);

            assertFalse(loaded.created());
            assertEquals(stored.result(), loaded.result());
            assertTrue(replay.goldWrites.isEmpty());
            assertTrue(replay.progressWrites.isEmpty());
        }
    }

    @Test
    void rollsBackGoldOverflowAndReusesTheOriginalDrawAfterATransientWriteFailure() {
        var result = RewardTestFixtures.result(1, 2, 99);
        var overflow = new MatchJdbcStub(result);
        overflow.gold = 0xffff_ffffL;
        var repository = new JdbcMatchResultRepository(overflow.source(), 5, TestLevels.table());

        assertThrows(IllegalArgumentException.class, () -> repository.settle(result));
        assertTrue(overflow.calls.contains("connection.rollback"));
        assertFalse(overflow.calls.contains("connection.commit"));

        var retry = new MatchJdbcStub(result);
        retry.fail("progress.executeUpdate", new SQLException("private-wallet-value", "40001"));
        var saved = new JdbcMatchResultRepository(retry.source(), 5, TestLevels.table()).settle(result);

        assertEquals(99, saved.result().participants().getFirst().card().draw());
        assertEquals(200, saved.result().participants().getFirst().card().amount());
        assertTrue(retry.calls.contains("connection.rollback"));
        assertTrue(retry.calls.contains("connection.commit"));
    }
}
