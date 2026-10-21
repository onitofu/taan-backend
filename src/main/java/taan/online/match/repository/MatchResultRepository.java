package taan.online.match.repository;

import taan.online.match.model.MatchResult;

public interface MatchResultRepository {
    MatchSettlement settle(MatchResult result);
}
