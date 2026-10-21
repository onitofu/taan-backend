package taan.online.match.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import taan.online.character.model.CharacterProgress;
import taan.online.match.model.MatchExperienceRules;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;

final class MatchExperiencePersistence {
    private final MatchExperienceRules rules;
    private final int timeout;

    MatchExperiencePersistence(MatchExperienceRules rules, int timeout) {
        this.rules = rules;
        this.timeout = timeout;
    }

    MatchResult settle(Connection connection, MatchResult result) throws SQLException {
        var rewards = new ArrayList<MatchParticipantResult>();
        var ordered = result.participants().stream()
            .sorted(Comparator.comparingLong(p -> p.characterId().value())).toList();

        for (var participant : ordered) {
            CharacterProgress before;

            try (var statement = connection.prepareStatement(
                "SELECT experience, level, force_medals, kill_medals, supply_medals "
                    + "FROM characters WHERE id = ? FOR UPDATE"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, participant.characterId().value());

                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("Reward character is missing", "23503");
                    }

                    before = new CharacterProgress(
                        rows.getLong("experience"),
                        rows.getLong("force_medals"),
                        rows.getLong("kill_medals"),
                        rows.getLong("supply_medals")
                    );
                }
            }

            var reward = rules.calculate(result, participant, before);
            var after = reward.after();

            try (var statement = connection.prepareStatement(
                "UPDATE characters SET experience = ?, level = ?, force_medals = ?, kill_medals = ? WHERE id = ?"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, after.progress().experience());
                statement.setInt(2, after.level());
                statement.setLong(3, after.progress().forceMedals());
                statement.setLong(4, after.progress().killMedals());
                statement.setLong(5, participant.characterId().value());

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Reward character update failed", "22000");
                }
            }

            rewards.add(participant.rewarded(reward));
        }

        rewards.sort(Comparator.comparingInt(MatchParticipantResult::slotIndex));
        return result.rewarded(rewards);
    }
}
