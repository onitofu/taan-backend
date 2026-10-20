package taan.online.match.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MatchScenarioCommitRules {
    private MatchScenarioCommitRules() {
    }

    public static MatchMovementState movement(
        MatchWorldState current,
        MatchWorldState prepared,
        UUID active,
        MatchMovementState moving
    ) {
        if (prepared.falls().containsKey(active)
            || current.actors().get(active).y() != prepared.actors().get(active).y()
            || prepared.actors().get(active).dead()) {
            return new MatchMovementState(moving.allowance(), Optional.empty());
        }

        return moving;
    }

    public static boolean permits(
        MatchPhase phase,
        MatchWorldState current,
        MatchTurn turn,
        MatchWorldState prepared,
        List<MatchParticipant> participants
    ) {
        return phase == MatchPhase.ACTION && prepared.revision() == current.revision() + 1
            && prepared.receipt().filter(
                value -> value.worldRevision() == prepared.revision() && value.turnRevision() == turn.revision()
                    && participants.stream().anyMatch(
                        member -> member.sessionId().equals(value.sessionId())
                            && member.admissionId().equals(value.admissionId())
                    )
            ).isPresent();
    }
}
