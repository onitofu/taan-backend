package taan.online.match.service;

final class MatchReportBudget {
    private long windowStartedNanos;
    private int count;

    MatchReportBudget(long now) {
        windowStartedNanos = now;
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
