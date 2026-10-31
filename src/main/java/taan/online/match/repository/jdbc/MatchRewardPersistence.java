package taan.online.match.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import taan.online.character.model.CharacterProgress;
import taan.online.match.model.MatchMedalLimitException;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardCalculation;
import taan.online.match.model.MatchRewardPolicy;

final class MatchRewardPersistence {
    private final int timeout;

    MatchRewardPersistence(int timeout) {
        this.timeout = timeout;
    }

    MatchResult settle(Connection connection, MatchResult result, MatchRewardPolicy policy) throws SQLException {
        var rewards = new ArrayList<MatchParticipantResult>();
        var ordered = result.participants().stream()
            .sorted(Comparator.comparingLong(p -> p.characterId().value())).toList();

        for (var participant : ordered) {
            CharacterProgress before;
            long goldBefore;
            int levelBefore;

            try (var statement = connection.prepareStatement(
                "SELECT experience, gold, level, force_medals, kill_medals, supply_medals "
                    + "FROM characters WHERE id = ? FOR UPDATE"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, participant.characterId().value());

                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Reward character is missing", "23503");
                    }

                    goldBefore = rows.getLong("gold");
                    levelBefore = rows.getInt("level");
                    before = new CharacterProgress(
                        rows.getLong("experience"),
                        rows.getLong("force_medals"),
                        rows.getLong("kill_medals"),
                        rows.getLong("supply_medals")
                    );
                }
            }

            MatchRewardCalculation calculation;

            try {
                calculation = policy.calculate(result, participant, before, levelBefore, goldBefore);
            } catch (MatchMedalLimitException failure) {
                throw new SQLException("Character medal counter cannot be incremented", "22003", failure);
            }

            var after = calculation.progress();

            try (var statement = connection.prepareStatement(
                "UPDATE characters SET experience = ?, level = ?, force_medals = ?, kill_medals = ?, "
                    + "gold = ? WHERE id = ?"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, after.experience());
                statement.setInt(2, calculation.level());
                statement.setLong(3, after.forceMedals());
                statement.setLong(4, after.killMedals());
                statement.setLong(5, calculation.gold());
                statement.setLong(6, participant.characterId().value());

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Reward character update failed", "22000");
                }
            }

            rewards.add(calculation.participant());
        }

        rewards.sort(Comparator.comparingInt(MatchParticipantResult::slotIndex));
        return result.rewarded(rewards);
    }
}
