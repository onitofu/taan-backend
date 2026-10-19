package taan.online.match.model;

import java.util.Optional;

public record MatchMovementState(MovementAllowance allowance, Optional<MatchWalkingFall> fall) {
}
