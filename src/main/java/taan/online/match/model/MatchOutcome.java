package taan.online.match.model;

import java.util.Set;
import taan.online.room.model.RoomTeam;

public record MatchOutcome(Set<RoomTeam> winningTeams, boolean awardsGranted) {
    public MatchOutcome {
        winningTeams = Set.copyOf(winningTeams);
    }

    public static MatchOutcome previewAborted() {
        return new MatchOutcome(Set.of(), false);
    }
}
