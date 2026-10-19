package taan.online.match.model;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class MatchMovementRules {
    private MatchMovementRules() {
    }

    static MatchMovementState admit(
        MatchMovementState previous,
        MatchActorState actor,
        int x,
        int y,
        int facing,
        MatchMapDefinition map,
        List<TerrainLayerDefinition> terrain,
        long now
    ) {
        var allowance = previous.allowance();

        if (x < 0 || x >= map.width() || y < 0 || y > (long) map.height() + 200 || facing < 0 || facing > 1) {
            throw new ScenarioWorldException("MOVEMENT_OUTSIDE_MAP");
        }

        long spent = allowance.spentMillis() + Math.abs((long) x - actor.x()) * allowance.stepPeriodMillis();

        if (now - allowance.observedAtNanos() < 0 || spent > allowance.initialBudgetMillis()
            || spent > TimeUnit.NANOSECONDS.toMillis(now - allowance.startedAtNanos()) + 500) {
            throw new ScenarioWorldException("MOVEMENT_ALLOWANCE_EXCEEDED");
        }

        var oldFall = previous.fall();
        Optional<MatchWalkingFall> fall;

        if (oldFall.isPresent() && x == actor.x() && y >= actor.y() && y <= oldFall.orElseThrow().endY()) {
            fall = oldFall;
        } else {
            int end = MatchTerrainSupport.supportY(terrain, x, y, map.height()).orElse(map.height() + 200);
            long duration = y > map.height() ? 3 : Math.max(0, 3L * (end - y) + 3);
            fall = end > y || y > map.height()
                ? Optional.of(new MatchWalkingFall(now, now + TimeUnit.MILLISECONDS.toNanos(duration), y, end))
                : Optional.empty();
        }

        return new MatchMovementState(
            new MovementAllowance(
                allowance.turnRevision(),
                allowance.startedAtNanos(),
                now,
                allowance.initialBudgetMillis(),
                (int) spent,
                allowance.stepPeriodMillis()
            ),
            fall
        );
    }
}
