package taan.online.match.repository.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import javax.sql.DataSource;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.match.model.MatchExperienceReward;
import taan.online.match.model.MatchExperienceRules;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.match.repository.MatchResultRepository;
import taan.online.match.repository.MatchSettlement;
import taan.online.room.model.RoomTeam;

public final class JdbcMatchResultRepository implements MatchResultRepository {
    private final DataSource dataSource;
    private final int timeout;
    private final MatchExperiencePersistence experience;

    public JdbcMatchResultRepository(DataSource dataSource, int timeout, CharacterLevelTable levels) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        experience = new MatchExperiencePersistence(new MatchExperienceRules(levels), timeout);
    }

    @Override
    public MatchSettlement settle(MatchResult result) {
        try {
            return transact(result);
        } catch (SQLException first) {
            String state = first.getSQLState();

            if (state == null || !state.startsWith("08") && !state.startsWith("40")) {
                throw new MatchPersistenceException(first);
            }

            try {
                return transact(result);
            } catch (SQLException second) {
                second.addSuppressed(first);
                throw new MatchPersistenceException(second);
            }
        }
    }

    private MatchSettlement transact(MatchResult result) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                boolean created = insert(connection, result);
                MatchResult stored;

                if (created) {
                    if (result.rewardDurationMillis() == null) {
                        grantMedals(connection, result);
                        stored = result;
                    } else {
                        stored = experience.settle(connection, result);
                    }

                    insertParticipants(connection, stored);
                } else {
                    stored = read(connection, result.matchId());
                }

                connection.commit();
                return new MatchSettlement(stored, created);
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }

                throw failure;
            }
        }
    }

    private boolean insert(Connection connection, MatchResult result) throws SQLException {
        String sql = """
            INSERT INTO match_results
                (match_id, finished_at, winning_team, cause, policy_version, reward_duration_millis)
            VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (match_id) DO NOTHING
            """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setObject(1, result.matchId().value());
            statement.setObject(2, result.finishedAt().atOffset(ZoneOffset.UTC));

            if (result.winningTeam().isPresent()) {
                statement.setShort(3, team(result.winningTeam().orElseThrow()));
            } else {
                statement.setNull(3, Types.SMALLINT);
            }

            statement.setString(4, result.cause().name());
            statement.setString(5, result.policyVersion());
            statement.setObject(6, result.rewardDurationMillis(), Types.BIGINT);
            return statement.executeUpdate() == 1;
        }
    }

    private void insertParticipants(Connection connection, MatchResult result) throws SQLException {
        String sql = """
            INSERT INTO match_participant_results
                (match_id, character_id, slot_index, team, damage, award_eligible, force_medal, kills, kill_medal,
                 experience_before, level_before, experience_after, level_after, experience_base_delta,
                 experience_pcroom_delta, experience_medal_delta, experience_guard_adjustment,
                 force_medals_after, kill_medals_after, supply_medals_after)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);

            for (var participant : result.participants()) {
                statement.setObject(1, result.matchId().value());
                statement.setLong(2, participant.characterId().value());
                statement.setInt(3, participant.slotIndex());
                statement.setShort(4, team(participant.team()));
                statement.setLong(5, participant.damage());
                statement.setBoolean(6, participant.awardEligible());
                statement.setBoolean(7, participant.forceMedal());
                statement.setInt(8, participant.kills());
                statement.setBoolean(9, participant.killMedal());
                var reward = participant.experienceReward();

                if (reward == null) {
                    for (int column = 10; column <= 20; column++) {
                        statement.setNull(column, Types.BIGINT);
                    }
                } else {
                    statement.setLong(10, reward.experienceBefore());
                    statement.setInt(11, reward.levelBefore());
                    statement.setLong(12, reward.after().progress().experience());
                    statement.setInt(13, reward.after().level());
                    statement.setLong(14, reward.baseDelta());
                    statement.setLong(15, reward.pcroomDelta());
                    statement.setLong(16, reward.medalDelta());
                    statement.setLong(17, reward.guardAdjustment());
                    statement.setLong(18, reward.after().progress().forceMedals());
                    statement.setLong(19, reward.after().progress().killMedals());
                    statement.setLong(20, reward.after().progress().supplyMedals());
                }
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void grantMedals(Connection connection, MatchResult result) throws SQLException {
        String sql = """
            UPDATE characters SET force_medals = force_medals + 1
            WHERE id = ? AND force_medals < 4294967295
            """;
        var recipients = result.participants().stream().filter(MatchParticipantResult::forceMedal)
            .sorted(Comparator.comparingLong(value -> value.characterId().value())).toList();

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);

            for (var recipient : recipients) {
                statement.setLong(1, recipient.characterId().value());

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Character medal counter cannot be incremented", "22003");
                }
            }
        }
    }

    private MatchResult read(Connection connection, MatchId id) throws SQLException {
        String sql = "SELECT finished_at, winning_team, cause, policy_version, reward_duration_millis "
            + "FROM match_results WHERE match_id = ?";

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setObject(1, id.value());

            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Committed match result is missing", "22000");
                }

                var winner = rows.getObject("winning_team") == null ? Optional.<RoomTeam>empty()
                    : Optional.of(team(rows.getShort("winning_team")));
                return new MatchResult(
                    id,
                    rows.getObject("finished_at", OffsetDateTime.class).toInstant(),
                    winner,
                    MatchOutcomeCause.valueOf(rows.getString("cause")),
                    rows.getString("policy_version"),
                    readParticipants(connection, id),
                    (Long) rows.getObject("reward_duration_millis")
                );
            }
        }
    }

    private ArrayList<MatchParticipantResult> readParticipants(Connection connection, MatchId id) throws SQLException {
        String sql = """
            SELECT character_id, slot_index, team, damage, award_eligible, force_medal, kills, kill_medal,
                   experience_before, level_before, experience_after, level_after, experience_base_delta,
                   experience_pcroom_delta, experience_medal_delta, experience_guard_adjustment,
                   force_medals_after, kill_medals_after, supply_medals_after
            FROM match_participant_results WHERE match_id = ? ORDER BY slot_index
            """;
        var participants = new ArrayList<MatchParticipantResult>();

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            statement.setObject(1, id.value());

            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    participants.add(
                        new MatchParticipantResult(
                            new CharacterId(rows.getLong("character_id")),
                            rows.getInt("slot_index"),
                            team(rows.getShort("team")),
                            rows.getLong("damage"),
                            rows.getBoolean("award_eligible"),
                            rows.getBoolean("force_medal"),
                            rows.getInt("kills"),
                            rows.getBoolean("kill_medal"),
                            null,
                            readExperience(rows)
                        )
                    );
                }
            }
        }

        return participants;
    }

    private static MatchExperienceReward readExperience(ResultSet rows) throws SQLException {
        if (rows.getObject("experience_before") == null) {
            return null;
        }

        return new MatchExperienceReward(
            rows.getLong("experience_before"),
            rows.getInt("level_before"),
            new CharacterProgressSnapshot(
                new CharacterId(rows.getLong("character_id")),
                rows.getInt("level_after"),
                new CharacterProgress(
                    rows.getLong("experience_after"),
                    rows.getLong("force_medals_after"),
                    rows.getLong("kill_medals_after"),
                    rows.getLong("supply_medals_after")
                )
            ),
            rows.getLong("experience_base_delta"),
            rows.getLong("experience_pcroom_delta"),
            rows.getLong("experience_medal_delta"),
            rows.getLong("experience_guard_adjustment")
        );
    }

    private static short team(RoomTeam team) {
        return (short) (team == RoomTeam.BLUE ? 1 : 2);
    }

    private static RoomTeam team(short value) throws SQLException {
        return switch (value) {
            case 1 -> RoomTeam.BLUE;
            case 2 -> RoomTeam.RED;
            default -> throw new SQLException("Invalid stored match team", "22000");
        };
    }
}
