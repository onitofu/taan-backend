package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.ScenarioApplicationOutcome;

class ScenarioAnimationTest {
    @Test
    void acceptsSpecialAndCriticalAnimationsWithoutChangingTheirGameplayEffects() throws Exception {
        for (int kind : new int[]{14, 16}) {
            try (var e = new EnergyRuntimeFixture()) {
                boolean special = kind == 14;

                if (special) {
                    e.charge();
                    assertTrue(e.activate(e.use()));
                }

                assertTrue(e.record(e.spend(List.of())));
                var command = animation(e, special, kind, e.use().slot(), 0);
                var result = e.f.runtime.applyScenario(command);
                e.f.run();

                assertEquals(ScenarioApplicationOutcome.APPLIED, result.join().outcome(), result.join().reason());
                assertEquals(0, e.snapshot().actors().get(e.context().sessionId()).energy());
                e.acknowledge();
                assertEquals(MatchPhase.ACTION, e.snapshot().phase());
                assertNull(e.f.callbacks.reason);
            }
        }
    }

    @Test
    void rejectsForeignMarkersNonzeroDurationsAndUnauthorizedSpecialsBeforeApplyingTheWorld() throws Exception {
        for (int[] value : new int[][]{{14, 0, 0}, {14, 2, 0}, {16, 2, 0}, {14, 0, 1}, {16, 0, 1}}) {
            try (var e = new EnergyRuntimeFixture()) {
                boolean special = value[1] != 0 || value[2] != 0;

                if (special) {
                    e.charge();
                    assertTrue(e.activate(e.use()));
                }

                assertTrue(e.record(e.spend(List.of())));
                var before = e.snapshot().world();
                var command = animation(e, special, value[0], value[1], value[2]);
                var result = e.f.runtime.applyScenario(command);
                e.f.run();

                assertEquals(ScenarioApplicationOutcome.REJECTED, result.join().outcome());
                assertEquals(
                    value[1] != 0 ? "INVALID_COMMAND_OWNER" : value[2] != 0
                        ? "INVALID_ANIMATION_DURATION" : "UNAUTHORIZED_SPECIAL_ANIMATION",
                    result.join().reason()
                );
                assertEquals(before, e.snapshot().world());
            }
        }
    }

    private ApplyScenarioCommand animation(EnergyRuntimeFixture e, boolean special, int kind, int slot, int duration)
        throws Exception {
        var source = e.shot(special, 0);
        var codec = new ScenarioCodec();
        var packet = codec.decode(source.root());
        var s = packet.scenario();
        var commands = new ArrayList<>(s.commands());
        commands.addFirst(new ScenarioNormalCommand(new ScenarioCommandHeader(slot, kind, 0, duration), 39, -1));
        var modified = new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                s.slot(),
                s.scalar09(),
                commands,
                s.selectedItems(),
                s.effects(),
                s.flags(),
                s.power(),
                s.refittedSlots(),
                s.scalar0c()
            ),
            packet.scalar18(),
            packet.scalar1c(),
            packet.tail()
        );
        return new ApplyScenarioCommand(
            source.context(),
            source.turnRevision(),
            source.worldRevision(),
            new ScenarioClaimsMapper().map(modified),
            codec.encode(modified),
            source.phaseRevision()
        );
    }
}
