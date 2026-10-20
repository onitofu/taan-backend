package taan.online.match.model;

import java.util.Optional;

public record MatchMovementState(MovementAllowance allowance, Optional<MatchWalkingFall> fall) {
    public static MatchMovementState initial(long turnRevision, long now, int budget) {
        return new MatchMovementState(new MovementAllowance(turnRevision, now, now, budget, 0, 20), Optional.empty());
    }
}
