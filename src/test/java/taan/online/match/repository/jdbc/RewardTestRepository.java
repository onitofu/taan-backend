package taan.online.match.repository.jdbc;

import javax.sql.DataSource;
import taan.online.character.model.CharacterLevelTable;
import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardPolicies;
import taan.online.match.repository.MatchSettlement;

final class RewardTestRepository {
    private final JdbcMatchResultRepository repository;
    private final MatchRewardPolicies policies;

    RewardTestRepository(DataSource source, int timeout, CharacterLevelTable levels) {
        repository = new JdbcMatchResultRepository(source, timeout);
        policies = new MatchRewardPolicies(levels);
    }

    MatchSettlement settle(MatchResult result) {
        return repository.settle(result, policies.select(result));
    }
}
