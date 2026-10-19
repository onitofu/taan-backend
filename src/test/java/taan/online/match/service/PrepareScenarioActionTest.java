package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioClaim;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioJournal;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;
import taan.online.match.model.ScenarioWorldException;

class PrepareScenarioActionTest {
    @Test
    void leavesOriginalWorldUntouchedWhenALateEventOrRefitCannotBeApplied() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var before = f.snapshot();
            var accepted = f.execute(f.claims).accepted().orElseThrow();
            var prepare = new PrepareScenarioAction();

            for (ScenarioClaim event : List.of(
                new ScenarioActorClaim(new ScenarioEventTime(7, 5, 4000, 0)),
                new ScenarioUnsupportedClaim(new ScenarioEventTime(2, 15, 4000, 0))
            )) {
                var events = new ArrayList<>(accepted.journal().events());
                events.add(event);
                var journal = new ScenarioJournal(events, accepted.journal().missileDefinitions(), List.of(), 5000);
                var malformed = new AcceptedScenario(
                    accepted.sender(),
                    accepted.turnRevision(),
                    accepted.root(),
                    journal
                );

                assertThrows(ScenarioWorldException.class, () -> prepare.execute(before, malformed));
                assertEquals(before.world(), f.snapshot().world());
            }

            var missingRefit = new ScenarioJournal(
                accepted.journal().events(),
                accepted.journal().missileDefinitions(),
                List.of(7),
                5000
            );
            assertThrows(
                ScenarioWorldException.class,
                () -> prepare.execute(before, new AcceptedScenario(f.sender, 7, f.root, missingRefit))
            );
            var unknownMissile = new ScenarioJournal(accepted.journal().events(), Map.of(), List.of(), 5000);
            assertThrows(
                ScenarioWorldException.class,
                () -> prepare.execute(before, new AcceptedScenario(f.sender, 7, f.root, unknownMissile))
            );
        }
    }

    @Test
    void appliesExplicitDeathBeforeLethalDamageAndDeduplicatesItWithinOneJournal() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var accepted = f.execute(f.claims).accepted().orElseThrow();
            var events = new ArrayList<>(accepted.journal().events());
            events.addFirst(new ScenarioActorClaim(new ScenarioEventTime(2, 5, 0, 0)));
            events.add(new ScenarioActorClaim(new ScenarioEventTime(2, 5, 4000, 0)));
            var journal = new ScenarioJournal(events, accepted.journal().missileDefinitions(), List.of(), 5000);
            var result = new PrepareScenarioAction().execute(
                f.snapshot(),
                new AcceptedScenario(f.sender, 7, f.root, journal)
            );

            assertEquals(1, result.deaths());
            assertEquals(0, result.world().actors().get(f.sender.sessionId()).hp());
        }
    }

    @Test
    void rejectsExplosionScaleOutsideTheCatalogContract() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var claims = new ArrayList<>(f.claims.commands());
            var event = (ScenarioProjectileClaim) claims.get(3);
            claims.set(
                3,
                new ScenarioProjectileClaim(
                    event.header(),
                    event.sequence(),
                    event.variant(),
                    event.shotTick(),
                    2,
                    event.x(),
                    event.y()
                )
            );

            assertEquals("INVALID_TERRAIN", f.execute(f.commands(claims)).reason());
        }
    }
}
