package taan.online;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import taan.online.match.model.MatchCombatStatistics;
import taan.online.match.model.MatchId;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchResult;
import taan.online.match.repository.MatchSettlement;
import taan.online.match.service.SettleMatchResultAction;
import taan.online.match.service.model.MatchEndResult;

public final class TestMatchResults {
    private TestMatchResults() {
    }

    public static SettleMatchResultAction action() {
        var results = new ConcurrentHashMap<MatchId, MatchResult>();
        return new SettleMatchResultAction(
            result -> {
                var previous = results.putIfAbsent(result.matchId(), result);
                return new MatchSettlement(previous == null ? result : previous, previous == null);
            },
            Runnable::run,
            Clock.systemUTC()
        );
    }

    public static MatchEndResult end(MatchOutcome outcome) {
        var result = MatchResult.finish(
            new MatchId(UUID.randomUUID()),
            Instant.EPOCH,
            outcome,
            List.of(),
            MatchCombatStatistics.empty(),
            Map.of()
        );
        return new MatchEndResult(result, List.of());
    }
}
