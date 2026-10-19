package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.model.CannonShotDefinition;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioClaims;

class ScenarioPowerAdmissionTest {
    @Test
    void obeysCatalogPowerBoundsAndAcceptsTheirExactEndpoints() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var original = world.definitions;
            world.definitions = new MatchDefinitions(
                original.catalogVersion(),
                original.map(),
                original.weapons(),
                new CannonShotDefinition(0, 0, 25, 75, 0, 0),
                MatchFixtures.missileTerrain()
            );

            for (float power : new float[]{.24f, .76f}) {
                assertEquals(ScenarioAdmissionOutcome.REJECTED, world.execute(power(world, power)).outcome());
            }

            for (float power : new float[]{.25f, .75f}) {
                assertEquals(ScenarioAdmissionOutcome.ACCEPTED, world.execute(power(world, power)).outcome());
            }

            world.definitions = original;

            for (float power : new float[]{0, 1}) {
                assertEquals(ScenarioAdmissionOutcome.ACCEPTED, world.execute(power(world, power)).outcome());
            }
        }
    }

    private ScenarioClaims power(ScenarioTestWorld world, float power) {
        var claims = world.claims;
        return new ScenarioClaims(
            claims.shooterSlot(),
            claims.scenarioSlot(),
            claims.facing(),
            claims.flags(),
            power,
            claims.selectedItems(),
            claims.effectsPresent(),
            claims.refittedSlots(),
            claims.commands()
        );
    }
}
