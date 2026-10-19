package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class MatchMovementRulesTest {
    @Test
    void rejectsMapEnvelopeFacingAndBackwardsClockWithoutChangingTheLedger() {
        var map = MatchFixtures.definitions().map();
        var spawn = map.spawns().getFirst();
        var actor = new MatchActorState(spawn.settledX(), spawn.settledY(), 0, 1000, 0);
        var allowance = new MovementAllowance(1, 100, 100, 2500, 0, 20);
        var original = new MatchMovementState(allowance, Optional.empty());

        for (int[] value : new int[][]{
            {-1, actor.y(), 0}, {map.width(), actor.y(), 0}, {actor.x(), -1, 0},
            {actor.x(), map.height() + 201, 0}, {actor.x(), actor.y(), -1}, {actor.x(), actor.y(), 2}
        }) {
            assertThrows(
                ScenarioWorldException.class,
                () -> MatchMovementRules.admit(
                    original,
                    actor,
                    value[0],
                    value[1],
                    value[2],
                    map,
                    map.terrainLayers(),
                    100
                )
            );
        }

        assertThrows(
            ScenarioWorldException.class,
            () -> MatchMovementRules.admit(
                original,
                actor,
                actor.x(),
                actor.y(),
                0,
                map,
                map.terrainLayers(),
                99
            )
        );
        assertEquals(0, original.allowance().spentMillis());
        assertTrue(original.fall().isEmpty());
    }

    @Test
    void tracksWalkingFallWithoutRestartingItForIntermediateAnchors() {
        var map = MatchFixtures.definitions().map();
        var spawn = map.spawns().getFirst();
        var actor = new MatchActorState(spawn.settledX(), spawn.settledY(), 0, 1000, 0);
        var original = new MatchMovementState(new MovementAllowance(1, 0, 0, 2500, 0, 20), Optional.empty());
        var airborne = MatchMovementRules.admit(
            original,
            actor,
            actor.x(),
            actor.y() - 20,
            0,
            map,
            map.terrainLayers(),
            0
        );
        var anchor = new MatchActorState(actor.x(), actor.y() - 20, 0, 1000, 0);
        var continued = MatchMovementRules.admit(
            airborne,
            anchor,
            actor.x(),
            actor.y() - 10,
            1,
            map,
            map.terrainLayers(),
            10000000
        );
        assertSame(airborne.fall(), continued.fall());
        assertEquals(63000000, airborne.fall().orElseThrow().endsAtNanos());
        assertEquals(actor.y(), airborne.fall().orElseThrow().endY());

        var raised = MatchMovementRules.admit(
            airborne,
            anchor,
            actor.x(),
            actor.y() - 21,
            0,
            map,
            map.terrainLayers(),
            10000000
        );
        assertEquals(76000000, raised.fall().orElseThrow().endsAtNanos());
        var lowered = MatchMovementRules.admit(
            airborne,
            anchor,
            actor.x(),
            actor.y() + 50,
            0,
            map,
            map.terrainLayers(),
            10000000
        );
        assertTrue(lowered.fall().isEmpty());
        var shifted = MatchMovementRules.admit(
            airborne,
            anchor,
            actor.x() + 1,
            actor.y() - 20,
            0,
            map,
            map.terrainLayers(),
            10000000
        );
        assertEquals(20, shifted.allowance().spentMillis());
        assertEquals(0, original.allowance().spentMillis());
    }
}
