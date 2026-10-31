package taan.online.match.repository;

import taan.online.match.model.MatchResult;
import taan.online.match.model.MatchRewardPolicy;

public interface MatchResultRepository {
    MatchSettlement settle(MatchResult result, MatchRewardPolicy policy);
}
