package taan.online.match.service.model;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import taan.online.match.model.MatchBoxPolicy;

public record MatchBootstrapSettings(
    long loadingTimeoutNanos,
    long previewTimeoutNanos,
    int reportsPerSecond,
    MatchExecutionMode executionMode,
    long actionTimeoutNanos,
    long completionGraceNanos,
    MatchLootPolicy lootPolicy,
    MatchBoxPolicy boxPolicy
) {
    public MatchBootstrapSettings(
        long loadingTimeoutNanos,
        long previewTimeoutNanos,
        int reportsPerSecond,
        MatchExecutionMode executionMode,
        long actionTimeoutNanos,
        long completionGraceNanos,
        MatchLootPolicy lootPolicy
    ) {
        this(
            loadingTimeoutNanos,
            previewTimeoutNanos,
            reportsPerSecond,
            executionMode,
            actionTimeoutNanos,
            completionGraceNanos,
            lootPolicy,
            MatchBoxPolicy.DISABLED
        );
    }

    public MatchBootstrapSettings(
        long loadingTimeoutNanos,
        long previewTimeoutNanos,
        int reportsPerSecond,
        MatchExecutionMode executionMode,
        long actionTimeoutNanos,
        long completionGraceNanos
    ) {
        this(
            loadingTimeoutNanos,
            previewTimeoutNanos,
            reportsPerSecond,
            executionMode,
            actionTimeoutNanos,
            completionGraceNanos,
            MatchLootPolicy.disabled()
        );
    }

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
        return executionMode == MatchExecutionMode.CLIENT_SCENARIO && !eligible
            ? new MatchBootstrapSettings(
                loadingTimeoutNanos,
                previewTimeoutNanos,
                reportsPerSecond,
                MatchExecutionMode.PREVIEW,
                actionTimeoutNanos,
                completionGraceNanos,
                MatchLootPolicy.disabled(),
                MatchBoxPolicy.DISABLED
            ) : this;
    }

    public MatchBootstrapSettings(long loadingTimeoutNanos, long previewTimeoutNanos, int reportsPerSecond) {
        this(loadingTimeoutNanos, previewTimeoutNanos, reportsPerSecond, MatchExecutionMode.PREVIEW);
    }

    public MatchBootstrapSettings {
        Objects.requireNonNull(executionMode);
        Objects.requireNonNull(lootPolicy);
        Objects.requireNonNull(boxPolicy);
        if (loadingTimeoutNanos <= 0 || previewTimeoutNanos <= 0 || reportsPerSecond <= 0
            || actionTimeoutNanos <= 0 || completionGraceNanos <= 0) {
            throw new IllegalArgumentException("Invalid match bootstrap settings");
        }
    }
}
