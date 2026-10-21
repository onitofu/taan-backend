package taan.online.match.service;

import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import taan.online.logging.WarningEvent;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.model.MatchPhase;

final class MatchReportBudget {
    private long windowStartedNanos;
    private int count;

    MatchReportBudget(long now) {
        windowStartedNanos = now;
    }

    static boolean report(
        MatchPhase phase,
        Map<UUID, MatchReportBudget> budgets,
        UUID session,
        LongSupplier clock,
        int limit,
        WarningLogLimiter warnings
    ) {
        if (phase == MatchPhase.RESERVED) {
            return false;
        }

        long now = clock.getAsLong();
        var budget = budgets.computeIfAbsent(session, ignored -> new MatchReportBudget(now));

        if (!budget.accept(now, limit)) {
            warnings.warn(WarningEvent.LIMIT_EXCEEDED, "MATCH_REPORT_RATE_EXCEEDED");
            return false;
        }

        return true;
    }

    boolean accept(long now, int limit) {
        if (now - windowStartedNanos >= 1_000_000_000L) {
            windowStartedNanos = now;
            count = 0;
        }

        if (count >= limit) {
            return false;
        }

        count++;
        return true;
    }
}
