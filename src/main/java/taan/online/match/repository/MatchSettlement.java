package taan.online.match.repository;

import taan.online.match.model.MatchResult;

public record MatchSettlement(MatchResult result, boolean created) {
}
