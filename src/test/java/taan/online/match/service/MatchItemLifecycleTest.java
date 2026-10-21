package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import taan.online.MatchDamageUpTestPackets;
import taan.online.MatchGuidedTestPackets;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchSpreadTestPackets;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchItemLifecycleTest {
    @ParameterizedTest(name = "item {0}")
    @ValueSource(ints = {3, 7, 1005, 1050})
    void keepsTheSelectionAcrossTheActionWatchdogUntilItsOwnersNextTurn(int item) throws Exception {
        try (var p = new MatchRepeatFixture(policy(item))) {
            p.grant(item, true);
            assertTrue(p.energy.activate(p.use(0, item)));
            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);

            p.energy.movement.timer(p.f.runtime.snapshot().deadlineNanos());
            p.energy.movement.timer(p.f.runtime.snapshot().closure().orElseThrow().stopDrainUntilNanos());
            assertEquals(2, p.f.runtime.snapshot().turn().activeSlot());
            assertEquals(selection, p.f.runtime.snapshot().attacks().get(p.f.room.owner));
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            assertEquals(0, p.f.runtime.snapshot().turn().activeSlot());
            assertEquals(selection, p.f.runtime.snapshot().attacks().get(p.f.room.owner));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var scene = shot(p, item);
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
        }
    }

    @ParameterizedTest(name = "item {0}")
    @ValueSource(ints = {3, 7, 1005, 1050})
    void clearsTheSelectionWhenItsOwnerDiesInTheScenarioOrWhileFalling(int item) throws Exception {
        for (boolean falling : new boolean[]{false, true}) {
            try (var p = new MatchRepeatFixture(policy(item))) {
                p.grant(item, true);
                assertTrue(p.energy.activate(p.use(0, item)));

                if (falling) {
                    var actor = p.f.runtime.snapshot().actors().get(p.f.room.owner);
                    int bottom = p.f.match.definitions().map().height() + 200;
                    var move = p.energy.movement.move(0, bottom - actor.y(), actor.facing(), MatchMovementKind.MOVE);
                    assertTrue(p.energy.movement.move(move));
                    assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                    p.energy.movement.timer(p.f.timers.deadlines.getFirst().deadlineNanos());
                } else {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var scene = shot(p, item);
                    var commands = new ArrayList<>(scene.scenario().commands());
                    commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(0, 5, 1300, 0)));
                    scene = MatchRepeatTestPackets.commands(scene, scene.scenario().selectedItems(), 0, commands);
                    assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
                }

                assertTrue(p.f.runtime.snapshot().actors().get(p.f.room.owner).dead());
                assertTrue(p.f.match.attacks().isEmpty());
                assertTrue(p.f.match.loot().inventory(p.f.room.owner).receipts().isEmpty());
            }
        }
    }

    @ParameterizedTest(name = "item {0}")
    @ValueSource(ints = {3, 7, 1005, 1050})
    void disconnectClearsBothUnspentAndStagedSelectionsAndANewMatchStartsEmpty(int item) throws Exception {
        for (boolean spending : new boolean[]{false, true}) {
            try (var p = new MatchRepeatFixture(policy(item))) {
                p.grant(item, true);
                assertTrue(p.energy.activate(p.use(0, item)));

                if (spending) {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                }

                var command = new DepartMatchCommand(p.energy.context(), RoomMatchEndReason.DISCONNECTED);
                var result = p.f.runtime.turnWork(() -> p.f.runtime.outcomes.depart(command));
                p.f.run();
                assertTrue(result.join());
                assertTrue(p.f.match.attacks().isEmpty());
                assertTrue(p.f.match.loot().inventories().isEmpty());
                assertTrue(p.f.owner.spends.isEmpty());
            }
        }

        try (var fresh = new MatchRepeatFixture(policy(item))) {
            assertTrue(fresh.f.runtime.snapshot().attacks().isEmpty());
            assertTrue(fresh.f.runtime.snapshot().loot().inventories().isEmpty());
        }
    }

    private MatchLootPolicy policy(int item) {
        return switch (item) {
            case 3 -> MatchSpreadTestPackets.POLICY;
            case 7 -> MatchGuidedTestPackets.POLICY;
            default -> MatchDamageUpTestPackets.POLICY;
        };
    }

    private PcGamePlayScenario shot(MatchRepeatFixture fixture, int item) throws IOException {
        return switch (item) {
            case 3 -> MatchSpreadTestPackets.shot(0, 0, List.of(item), 0, 1, .5f);
            case 7 -> MatchGuidedTestPackets.shot(0, 0, List.of(item), 0, 1);
            default -> fixture.shot(1);
        };
    }
}
