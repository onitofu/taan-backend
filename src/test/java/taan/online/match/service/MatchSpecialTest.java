package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.service.model.ApplyScenarioCommand;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.RecordMatchSpendCommand;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchSpecialTest {
    @Test
    void startsAtZeroChargesReusesTheSpecialAndDoesNotResetAgainAtScenarioCommit() throws Exception {
        try (var e = new EnergyRuntimeFixture(); var logs = new LogCapture()) {
            assertEquals(0, e.snapshot().actors().get(e.f.room.owner).energy());
            assertTrue(e.movement.pass(e.movement.pass()));
            assertEquals(0, e.snapshot().turn().energyGrant());
            assertTrue(e.movement.pass(e.movement.pass()));
            assertEquals(10, e.snapshot().turn().energyGrant());
            assertEquals(10, e.snapshot().actors().get(e.f.room.owner).energy());
            e.charge();
            var use = e.use();
            assertTrue(e.activate(use));
            assertTrue(e.activate(use));
            assertEquals(0, e.snapshot().actors().get(use.context().sessionId()).energy());
            assertEquals(1, e.f.owner.specials.size());
            assertEquals(1, e.f.guest.specials.size());
            assertEquals(MatchAttackSelection.SPECIAL, e.snapshot().attacks().get(use.context().sessionId()));

            assertTrue(e.movement.pass(e.movement.pass()));
            assertTrue(e.movement.pass(e.movement.pass()));
            assertEquals(10, e.snapshot().actors().get(use.context().sessionId()).energy());
            assertTrue(e.record(e.spend(List.of())));
            assertTrue(e.record(e.spend(List.of())));
            assertTrue(e.f.owner.spends.isEmpty());
            var shot = e.f.runtime.applyScenario(e.shot(true, 10));
            e.f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, shot.join().outcome());
            assertEquals(20, e.snapshot().actors().get(use.context().sessionId()).energy());
            assertTrue(e.snapshot().attacks().isEmpty());
            assertEquals(1, e.f.owner.spends.size());
            assertEquals(1, e.f.guest.spends.size());
            assertTrue(e.f.owner.scenarios.isEmpty());
            assertEquals(1, e.f.guest.scenarios.size());
            e.acknowledge();
            assertEquals(MatchPhase.ACTION, e.snapshot().phase());
            e.charge();
            assertTrue(e.activate(e.use()));
            assertTrue(e.record(e.spend(List.of(new SpentMatchItem(6, 255)))));
            var second = e.f.runtime.applyScenario(e.shot(true, 0));
            e.f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, second.join().outcome());
            e.acknowledge();
            assertNull(e.f.callbacks.reason);
            assertTrue(logs.text().contains("match.special.duplicate"));
            assertTrue(logs.text().contains("match.energy.scenario.applied"));
            assertFalse(logs.text().contains("123456"));
        }
    }

    @Test
    void rejectsInsufficientEnergyAndMissingOrConflictingSpendWithoutFakingSuccess() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            assertFalse(e.activate(e.use()));
            assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
            assertTrue(e.f.owner.specials.isEmpty());
        }

        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            assertTrue(e.activate(e.use()));
            var result = e.f.runtime.applyScenario(e.shot(true, 0));
            e.f.run();
            assertEquals("MISSING_SPEND", result.join().reason());
            assertTrue(e.f.owner.spends.isEmpty());
        }

        try (var e = new EnergyRuntimeFixture()) {
            assertTrue(e.record(e.spend(List.of())));
            var valid = e.spend(List.of());
            assertFalse(e.record(new RecordMatchSpendCommand(
                valid.context(),
                valid.turnRevision(),
                valid.phaseRevision(),
                valid.slot(),
                255,
                valid.teamClear12(),
                List.of()
            )));
            assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
        }
    }

    @Test
    void preservesSelectionAcrossWatchdogAndCancelsAnUnfinishedSpendSequence() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            var use = e.use();
            assertTrue(e.activate(use));
            e.movement.timer(e.snapshot().deadlineNanos());
            e.movement.timer(e.snapshot().closure().orElseThrow().stopDrainUntilNanos());
            assertEquals(MatchAttackSelection.SPECIAL, e.snapshot().attacks().get(use.context().sessionId()));
            assertTrue(e.movement.pass(e.movement.pass()));
            assertTrue(e.record(e.spend(List.of())));
            assertFalse(e.movement.pass(e.movement.pass()));
            assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
        }

        try (var e = new EnergyRuntimeFixture()) {
            assertTrue(e.record(e.spend(List.of())));
            e.movement.timer(e.snapshot().deadlineNanos());
            assertEquals(RoomMatchEndReason.INVALID_SCENARIO, e.f.callbacks.reason);
        }
    }

    @Test
    void acceptsCapturedSpecialFireAfterRemovingDeferredBoxAndEffectCommands() throws Exception {
        try (var e = new EnergyRuntimeFixture()) {
            e.charge();
            assertTrue(e.activate(e.use()));
            assertTrue(e.record(e.spend(List.of())));
            byte[] root;
            try (var input = getClass().getResourceAsStream("/match/00291-c2s-combat.hex")) {
                root = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replaceAll("\\s+", ""));
            }
            var captured = new ScenarioCodec().decode(root);
            var scene = captured.scenario();
            var packet = new PcGamePlayScenario(
                captured.facing(),
                captured.shooterSlot(),
                new GameScenario(
                    scene.slot(),
                    scene.scalar09(),
                    scene.commands().stream().filter(event -> event.header().kind() < 10).toList(),
                    scene.selectedItems(),
                    scene.effects(),
                    scene.flags(),
                    scene.power(),
                    scene.refittedSlots(),
                    scene.scalar0c()
                ),
                captured.scalar18(),
                captured.scalar1c(),
                captured.tail()
            );
            root = new ScenarioCodec().encode(packet);
            var result = e.f.runtime.applyScenario(new ApplyScenarioCommand(
                e.context(),
                e.snapshot().turn().revision(),
                e.snapshot().world().revision(),
                new ScenarioClaimsMapper().map(packet),
                root,
                e.snapshot().phaseRevision()
            ));
            e.f.run();
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome(), result.join().reason());
        }
    }

}
