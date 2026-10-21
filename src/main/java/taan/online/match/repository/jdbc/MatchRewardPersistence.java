package taan.online.match.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.model.CharacterProgress;
import taan.online.match.model.MatchExperienceRules;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardRules;

final class MatchRewardPersistence {
    private final MatchExperienceRules rules;
    private final int timeout;
    private final MatchRewardRules rewardRules;

    MatchRewardPersistence(CharacterLevelTable levels, int timeout) {
        this.rules = new MatchExperienceRules(levels);
        rewardRules = new MatchRewardRules(levels);
        this.timeout = timeout;
    }

    MatchResult settle(Connection connection, MatchResult result) throws SQLException {
        var rewards = new ArrayList<MatchParticipantResult>();
        var ordered = result.participants().stream()
            .sorted(Comparator.comparingLong(p -> p.characterId().value())).toList();

        for (var participant : ordered) {
            CharacterProgress before;
            long goldBefore;
            boolean full = result.policyVersion().equals("pvp-rewards-1.1.0");

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
                    before = new CharacterProgress(
                        rows.getLong("experience"),
                        rows.getLong("force_medals"),
                        rows.getLong("kill_medals"),
                        rows.getLong("supply_medals")
                    );
                }
            }

            var settled = full ? rewardRules.calculate(result, participant, before, goldBefore)
                : participant.rewarded(rules.calculate(result, participant, before));
            var reward = settled.experienceReward();
            var after = reward.after();

            try (var statement = connection.prepareStatement(
                "UPDATE characters SET experience = ?, level = ?, force_medals = ?, kill_medals = ?, "
                    + "gold = ? WHERE id = ?"
            )) {
                statement.setQueryTimeout(timeout);
                statement.setLong(1, after.progress().experience());
                statement.setInt(2, after.level());
                statement.setLong(3, after.progress().forceMedals());
                statement.setLong(4, after.progress().killMedals());
                statement.setLong(5, full ? settled.goldReward().after() : goldBefore);
                statement.setLong(6, participant.characterId().value());

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Reward character update failed", "22000");
                }
            }

            rewards.add(settled);
        }

        rewards.sort(Comparator.comparingInt(MatchParticipantResult::slotIndex));
        return result.rewarded(rewards);
    }
}
