package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import taan.online.match.service.model.ScenarioClaims;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioEventTime;
import taan.online.match.service.model.ScenarioJournal;
import taan.online.match.service.model.AcceptedScenario;

class ScenarioEnergyTest {
    @Test
    void grantsTheHeaderOnceThenAppliesPerActorMaximaTwiceIncludingSelfHitsWithoutDoublingHp() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var before = f.snapshot();
            var accepted = f.execute(f.claims).accepted().orElseThrow();
            var shooter = f.sender.sessionId();
            int slot = f.claims.shooterSlot();
            int other = slot == 0 ? 2 : 0;
            var victim = before.participants().stream().filter(p -> p.slotIndex() == other).findFirst().orElseThrow();
            var journal = new ScenarioJournal(
                List.of(
                    new ScenarioDamageClaim(new ScenarioEventTime(other, 4, 1, 0), 10, -1, 3, 12),
                    new ScenarioDamageClaim(new ScenarioEventTime(other, 4, 2, 0), 5, -1, 5, 4),
                    new ScenarioDamageClaim(new ScenarioEventTime(slot, 4, 3, 0), 7, -1, 2, 20),
                    new ScenarioDamageClaim(new ScenarioEventTime(slot, 4, 4, 0), 0, -1, 65535, 32768)
                ),
                Map.of(),
                List.of(),
                10,
                1004
            );
            var prepared = new PrepareScenarioAction().execute(
                before,
                new AcceptedScenario(accepted.sender(), accepted.turnRevision(), accepted.root(), journal)
            );
            assertEquals(70, prepared.world().actors().get(shooter).energy());
            assertEquals(44, prepared.world().actors().get(victim.sessionId()).energy());
            assertEquals(993, prepared.world().actors().get(shooter).hp());
            assertEquals(985, prepared.world().actors().get(victim.sessionId()).hp());
            assertEquals(before.world().terrain(), prepared.world().terrain());
            assertEquals(0, prepared.changedCells());
            assertEquals(20, before.actors().get(shooter).energy());
        }
    }

    @Test
    void acceptsZeroAndMaximumHeaderGrantForEitherAttackAndRejectsOutOfPolicyValues() throws Exception {
        for (int grant : new int[]{0, 100, 101}) {
            try (var e = new EnergyRuntimeFixture()) {
                var result = e.f.runtime.applyScenario(e.shot(false, grant));
                e.f.run();
                assertEquals(grant > 100 ? "INVALID_HEADER_ENERGY" : "APPLIED", result.join().reason());
            }

            try (var e = new EnergyRuntimeFixture()) {
                e.charge();
                assertTrue(e.activate(e.use()));
                assertTrue(e.record(e.spend(List.of())));
                var result = e.f.runtime.applyScenario(e.shot(true, grant));
                e.f.run();
                assertEquals(grant > 100 ? "INVALID_HEADER_ENERGY" : "APPLIED", result.join().reason());
            }
        }
    }

    @Test
    void rejectsANegativeApplicationHeaderBeforePreparingAnyWorldChanges() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var c = f.claims;
            var invalid = new ScenarioClaims(
                c.shooterSlot(),
                c.scenarioSlot(),
                c.facing(),
                c.flags(),
                c.power(),
                -1,
                c.selectedItems(),
                c.effectsPresent(),
                c.refittedSlots(),
                c.commands()
            );
            var shooter = f.members.stream().filter(p -> p.slotIndex() == c.shooterSlot()).findFirst().orElseThrow();
            assertThrows(
                ScenarioRejectedException.class,
                () -> new ScenarioRules().validate(f.snapshot(), invalid, shooter)
            );
        }
    }

}
