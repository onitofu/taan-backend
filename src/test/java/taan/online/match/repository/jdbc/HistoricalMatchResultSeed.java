package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import taan.online.CharacterTestDatabase;
import taan.online.match.model.MatchResult;

final class HistoricalMatchResultSeed {
    private HistoricalMatchResultSeed() {
    }

    static void insert(
        CharacterTestDatabase database,
        MatchResult proposal,
        long winner,
        long loser
    ) throws SQLException {
        try (var connection = database.source().getConnection()) {
            connection.setAutoCommit(false);

            try (var header = connection.prepareStatement(
                "INSERT INTO match_results "
                    + "(match_id, finished_at, winning_team, cause, policy_version, reward_duration_millis) "
                    + "VALUES (?, ?, 1, 'ELIMINATION', 'pvp-exp-1.0.2', 60000)"
            )) {
                header.setObject(1, proposal.matchId().value());
                header.setObject(2, OffsetDateTime.parse("2026-10-21T00:00:00Z"));
                header.executeUpdate();
            }

            try (var participant = connection.prepareStatement("""
                INSERT INTO match_participant_results
                    (match_id, character_id, slot_index, team, damage, award_eligible, force_medal, kills, kill_medal,
                     experience_before, level_before, experience_after, level_after, experience_base_delta,
                     experience_pcroom_delta, experience_medal_delta, experience_guard_adjustment,
                     force_medals_after, kill_medals_after, supply_medals_after)
                SELECT ?, id, CASE WHEN id = ? THEN 0 ELSE 2 END, CASE WHEN id = ? THEN 1 ELSE 2 END,
                       400, true, true, 2, true, 900, 1,
                       CASE WHEN id = ? THEN 1016 ELSE 952 END, 2,
                       CASE WHEN id = ? THEN 100 ELSE 45 END,
                       CASE WHEN id = ? THEN 10 ELSE 5 END,
                       CASE WHEN id = ? THEN 6 ELSE 2 END, 0, 1, 1, 0
                FROM characters WHERE id IN (?, ?)
                """)) {
                participant.setObject(1, proposal.matchId().value());

                for (int column = 2; column <= 8; column++) {
                    participant.setLong(column, winner);
                }

                participant.setLong(9, loser);
                assertEquals(2, participant.executeUpdate());
            }

            try (var balances = connection.createStatement()) {
                balances.executeUpdate("UPDATE characters SET level = 2, force_medals = 1, kill_medals = 1, "
                    + "experience = CASE WHEN id = " + winner + " THEN 1016 ELSE 952 END");
            }

            connection.commit();
        }
    }
}
