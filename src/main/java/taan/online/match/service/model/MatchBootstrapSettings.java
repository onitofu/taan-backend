package taan.online.match.service.model;

public record MatchBootstrapSettings(long loadingTimeoutNanos, long previewTimeoutNanos, int reportsPerSecond) {
    public MatchBootstrapSettings {
        if (loadingTimeoutNanos <= 0 || previewTimeoutNanos <= 0 || reportsPerSecond <= 0) {
            throw new IllegalArgumentException("Invalid match bootstrap settings");
        }
    }
}
