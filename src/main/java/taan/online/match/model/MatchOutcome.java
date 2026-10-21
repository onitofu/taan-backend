package taan.online.match.model;

import java.util.Objects;
import java.util.Set;
import taan.online.room.model.RoomTeam;

public record MatchOutcome(Set<RoomTeam> winningTeams, boolean awardsGranted, MatchOutcomeCause cause) {
    public MatchOutcome(Set<RoomTeam> winningTeams, boolean awardsGranted) {
        this(
            winningTeams,
            awardsGranted,
            winningTeams.isEmpty()
            ? MatchOutcomeCause.TECHNICAL_CANCELLATION : MatchOutcomeCause.ELIMINATION
        );
    }

    public MatchOutcome {
        winningTeams = Set.copyOf(winningTeams);
        Objects.requireNonNull(cause);
        if (winningTeams.size() > 1 || winningTeams.isEmpty() != (cause == MatchOutcomeCause.TECHNICAL_CANCELLATION)
            || awardsGranted && winningTeams.isEmpty()) {
            throw new IllegalArgumentException("Inconsistent match outcome");
        }
    }

    public static MatchOutcome previewAborted() {
        return new MatchOutcome(Set.of(), false, MatchOutcomeCause.TECHNICAL_CANCELLATION);
    }
}
