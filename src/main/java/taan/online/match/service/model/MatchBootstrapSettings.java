package taan.online.match.service.model;

import java.util.Objects;

public record MatchBootstrapSettings(
    long loadingTimeoutNanos,
    long previewTimeoutNanos,
    int reportsPerSecond,
    MatchExecutionMode executionMode
) {
    public MatchBootstrapSettings(long loadingTimeoutNanos, long previewTimeoutNanos, int reportsPerSecond) {
        this(loadingTimeoutNanos, previewTimeoutNanos, reportsPerSecond, MatchExecutionMode.PREVIEW);
    }

    public MatchBootstrapSettings {
        Objects.requireNonNull(executionMode);
        if (loadingTimeoutNanos <= 0 || previewTimeoutNanos <= 0 || reportsPerSecond <= 0) {
            throw new IllegalArgumentException("Invalid match bootstrap settings");
        }
    }
}
