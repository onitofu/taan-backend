package taan.online.match.repository.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import javax.sql.DataSource;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.match.model.MatchExperienceReward;
import taan.online.match.model.MatchGoldReward;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardCard;
import taan.online.match.model.MatchRewardCardKind;
import taan.online.match.model.MatchRewardPolicy;
import taan.online.match.model.MatchShotStatistics;
import taan.online.match.repository.MatchResultRepository;
import taan.online.match.repository.MatchSettlement;
import taan.online.room.model.RoomTeam;

public final class JdbcMatchResultRepository implements MatchResultRepository {
    private final DataSource dataSource;
    private final int timeout;
    private final MatchRewardPersistence experience;

    public JdbcMatchResultRepository(DataSource dataSource, int timeout) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        experience = new MatchRewardPersistence(timeout);
    }

    @Override
    public MatchSettlement settle(MatchResult result, MatchRewardPolicy policy) {
        try {
            return transact(result, policy);
        } catch (SQLException first) {
            String state = first.getSQLState();

            if (state == null || !state.startsWith("08") && !state.startsWith("40")) {
                throw new MatchPersistenceException(first);
            }

            try {
                return transact(result, policy);
            } catch (SQLException second) {
                second.addSuppressed(first);
                throw new MatchPersistenceException(second);
            }
        }
    }

    private MatchSettlement transact(MatchResult result, MatchRewardPolicy policy) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                boolean created = insert(connection, result);
                MatchResult stored;

                if (created) {
                    stored = experience.settle(connection, result, policy);

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
                 force_medals_after, kill_medals_after, supply_medals_after,
                 experience_card_delta, gold_before, gold_after, gold_base_delta, gold_medal_delta, gold_card_delta,
                 card_draw, card_kind, card_amount, accepted_shots, damaging_shots)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                statement.setLong(21, reward == null ? 0 : reward.cardDelta());
                var gold = participant.goldReward();

                if (gold == null) {
                    for (int column = 22; column <= 26; column++) {
                        statement.setNull(column, Types.BIGINT);
                    }
                } else {
                    statement.setLong(22, gold.before());
                    statement.setLong(23, gold.after());
                    statement.setLong(24, gold.baseDelta());
                    statement.setLong(25, gold.medalDelta());
                    statement.setLong(26, gold.cardDelta());
                }

                var card = participant.card();
                statement.setObject(27, card == null ? null : card.draw(), Types.SMALLINT);
                statement.setString(28, card == null ? null : card.kind().name());
                statement.setObject(29, card == null ? null : card.amount(), Types.BIGINT);
                var shots = participant.shots();
                statement.setObject(30, shots == null ? null : shots.acceptedShots(), Types.BIGINT);
                statement.setObject(31, shots == null ? null : shots.damagingShots(), Types.BIGINT);
                statement.addBatch();
            }

            statement.executeBatch();
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
                   force_medals_after, kill_medals_after, supply_medals_after, experience_card_delta,
                   gold_before, gold_after, gold_base_delta, gold_medal_delta, gold_card_delta,
                   card_draw, card_kind, card_amount, accepted_shots, damaging_shots
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
                            readExperience(rows),
                            readGold(rows),
                            rows.getObject("card_draw") == null ? null : new MatchRewardCard(
                                rows.getInt("card_draw"),
                                MatchRewardCardKind.valueOf(rows.getString("card_kind")),
                                rows.getLong("card_amount")
                            ),
                            rows.getObject("accepted_shots") == null ? null : new MatchShotStatistics(
                                rows.getLong("accepted_shots"),
                                rows.getLong("damaging_shots")
                            )
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
            rows.getLong("experience_guard_adjustment"),
            rows.getLong("experience_card_delta")
        );
    }

    private static MatchGoldReward readGold(ResultSet rows) throws SQLException {
        return rows.getObject("gold_before") == null ? null : new MatchGoldReward(
            rows.getLong("gold_before"),
            rows.getLong("gold_after"),
            rows.getLong("gold_base_delta"),
            rows.getLong("gold_medal_delta"),
            rows.getLong("gold_card_delta")
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
