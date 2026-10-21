package taan.online.match.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public record MatchParticipationState(
    OptionalLong startedAtNanos,
    OptionalLong endedAtNanos,
    UUID activeSession,
    long activeSinceNanos,
    Map<UUID, Long> longestTurnMillis,
    Set<UUID> voluntarilyPassed
) {
    public MatchParticipationState {
        longestTurnMillis = Map.copyOf(longestTurnMillis);
        voluntarilyPassed = Set.copyOf(voluntarilyPassed);
    }

    public static MatchParticipationState initial() {
        return new MatchParticipationState(OptionalLong.empty(), OptionalLong.empty(), null, 0, Map.of(), Set.of());
    }

    public MatchParticipationState start(UUID session, long now) {
        if (endedAtNanos.isPresent()) {
            return this;
        }

        var previous = finish(now, false);
        return new MatchParticipationState(
            startedAtNanos.isPresent() ? startedAtNanos : OptionalLong.of(now),
            endedAtNanos,
            session,
            now,
            previous.longestTurnMillis,
            previous.voluntarilyPassed
        );
    }

    public MatchParticipationState finish(long now, boolean passed) {
        if (activeSession == null) {
            return this;
        }

        var longest = new HashMap<>(longestTurnMillis);
        longest.merge(activeSession, Math.max(0, TimeUnit.NANOSECONDS.toMillis(now - activeSinceNanos)), Math::max);
        var passes = new HashSet<>(voluntarilyPassed);

        if (passed) {
            passes.add(activeSession);
        }

        return new MatchParticipationState(startedAtNanos, endedAtNanos, null, 0, longest, passes);
    }

    public MatchParticipationState stop(long now) {
        if (endedAtNanos.isPresent()) {
            return this;
        }

        var closed = finish(now, false);
        return new MatchParticipationState(
            startedAtNanos,
            OptionalLong.of(now),
            null,
            0,
            closed.longestTurnMillis,
            closed.voluntarilyPassed
        );
    }

    public long durationMillis() {
        if (startedAtNanos.isEmpty() || endedAtNanos.isEmpty()) {
            return 0;
        }

        return Math.clamp(
            TimeUnit.NANOSECONDS.toMillis(endedAtNanos.getAsLong() - startedAtNanos.getAsLong()),
            0,
            1800000
        );
    }
}
