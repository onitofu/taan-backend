package taan.online.match.service.model;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public record MatchBootstrapSettings(
    long loadingTimeoutNanos,
    long previewTimeoutNanos,
    int reportsPerSecond,
    MatchExecutionMode executionMode,
    long actionTimeoutNanos,
    long completionGraceNanos
) {
    public MatchBootstrapSettings(
        long loadingTimeoutNanos,
        long previewTimeoutNanos,
        int reportsPerSecond,
        MatchExecutionMode executionMode
    ) {
        this(
            loadingTimeoutNanos,
            previewTimeoutNanos,
            reportsPerSecond,
            executionMode,
            TimeUnit.SECONDS.toNanos(20),
            TimeUnit.SECONDS.toNanos(10)
        );
    }

    public MatchBootstrapSettings forProfile(boolean eligible) {
        return executionMode == MatchExecutionMode.CANNON_1V1 && !eligible
            ? new MatchBootstrapSettings(
                loadingTimeoutNanos,
                previewTimeoutNanos,
                reportsPerSecond,
                MatchExecutionMode.PREVIEW,
                actionTimeoutNanos,
                completionGraceNanos
            ) : this;
    }

    public MatchBootstrapSettings(long loadingTimeoutNanos, long previewTimeoutNanos, int reportsPerSecond) {
        this(loadingTimeoutNanos, previewTimeoutNanos, reportsPerSecond, MatchExecutionMode.PREVIEW);
    }

    public MatchBootstrapSettings {
        Objects.requireNonNull(executionMode);
        if (loadingTimeoutNanos <= 0 || previewTimeoutNanos <= 0 || reportsPerSecond <= 0
            || actionTimeoutNanos <= 0 || completionGraceNanos <= 0) {
            throw new IllegalArgumentException("Invalid match bootstrap settings");
        }
    }
}
