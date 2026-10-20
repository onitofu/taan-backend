package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.match.model.CannonShotDefinition;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioGuidedClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;

class ScenarioProjectileRulesTest {
    @Test
    void bindsDelayedLaunchesAndTracksTerminalsPerSequenceIncludingInterleavedEvents() {
        var rules = rules();
        assertFalse(rules.hasFire());
        rules.fire(fire(0, 700));
        rules.fire(fire(1, 1700));
        rules.event(event(1, 1700, 1800, 1));
        rules.event(event(0, 700, 2000, 2));
        rules.event(event(1, 1700, 2200, 3));
        rules.fire(fire(2, 2700));
        rules.event(event(2, 2700, 2800, 2));
        assertTrue(rules.hasFire());
        assertEquals(Map.of(0, 0, 1, 0, 2, 0), rules.missiles());
        assertThrows(UnsupportedOperationException.class, () -> rules.missiles().clear());
        assertEquals("DUPLICATE_FIRE", assertThrows(
            ScenarioRejectedException.class,
            () -> rules.fire(fire(1, 3000))
        ).getMessage());
        assertEquals("DUPLICATE_PROJECTILE_TERMINAL", assertThrows(
            ScenarioRejectedException.class,
            () -> rules.event(event(1, 1700, 2300, 2))
        ).getMessage());
    }

    @Test
    void rejectsUnauthorizedSequencesAndReferencesBeforeTheirFireWithoutRequiringAllAuthorizedLaunches() {
        for (int sequence : new int[]{-1, 3}) {
            assertEquals("INVALID_FIRE", assertThrows(
                ScenarioRejectedException.class,
                () -> rules().fire(fire(sequence, 700))
            ).getMessage());
        }

        var rules = rules();
        assertEquals("MISSING_PROJECTILE", assertThrows(
            ScenarioRejectedException.class,
            () -> rules.event(event(0, 700, 1200, 2))
        ).getMessage());
        rules.fire(fire(0, 700));
        assertEquals("INVALID_PROJECTILE_REFERENCE", assertThrows(
            ScenarioRejectedException.class,
            () -> rules.event(event(0, 700, 699, 2))
        ).getMessage());
        assertEquals(Map.of(0, 0), rules.missiles());
    }

    @Test
    void requiresGuidanceToReferenceAnAlreadyLaunchedProjectileWithoutInventingCoordinateBounds() {
        var rules = rules();
        var header = new ScenarioEventTime(0, 13, 800, 0);
        var guided = new ScenarioGuidedClaim(header, 0, 0, 700, -1, -2, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals("MISSING_PROJECTILE", assertThrows(
            ScenarioRejectedException.class,
            () -> rules.guided(guided)
        ).getMessage());
        rules.fire(fire(0, 700));
        rules.guided(guided);

        for (var invalid : List.of(
            new ScenarioGuidedClaim(header, 0, 1, 700, 0, 0, 0, 0),
            new ScenarioGuidedClaim(header, 0, 0, 701, 0, 0, 0, 0),
            new ScenarioGuidedClaim(new ScenarioEventTime(0, 13, 699, 0), 0, 0, 700, 0, 0, 0, 0)
        )) {
            assertEquals("INVALID_PROJECTILE_REFERENCE", assertThrows(
                ScenarioRejectedException.class,
                () -> rules.guided(invalid)
            ).getMessage());
        }
    }

    private ScenarioProjectileRules rules() {
        return new ScenarioProjectileRules(new CannonShotDefinition(0, 0, 0, 100, 0, 0), 3);
    }

    private ScenarioFireClaim fire(int sequence, int tick) {
        return new ScenarioFireClaim(new ScenarioEventTime(0, 0, tick, 0), sequence, 0, tick, 0, 0, 1, 2, 3, 4);
    }

    private ScenarioProjectileClaim event(int sequence, int shotTick, int tick, int kind) {
        return new ScenarioProjectileClaim(new ScenarioEventTime(0, kind, tick, 0), sequence, 0, shotTick, 1, 500, 650);
    }
}
