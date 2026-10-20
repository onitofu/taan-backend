package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchGuidedTestPackets;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.service.model.DepartMatchCommand;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchGuidedLifecycleTest {
    @Test
    void keepsTheSelectionAcrossTheActionWatchdogUntilItsOwnersNextTurn() throws Exception {
        try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
            p.grant(7, true);
            assertTrue(p.energy.activate(p.use(0, 7)));
            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);

            p.energy.movement.timer(p.f.runtime.snapshot().deadlineNanos());
            p.energy.movement.timer(p.f.runtime.snapshot().closure().orElseThrow().stopDrainUntilNanos());
            assertEquals(2, p.f.runtime.snapshot().turn().activeSlot());
            assertEquals(selection, p.f.runtime.snapshot().attacks().get(p.f.room.owner));
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            assertEquals(0, p.f.runtime.snapshot().turn().activeSlot());
            assertEquals(selection, p.f.runtime.snapshot().attacks().get(p.f.room.owner));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var scene = MatchGuidedTestPackets.shot(0, 0, List.of(7), 0, 1);
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
        }
    }

    @Test
    void clearsTheSelectionWhenItsOwnerDiesInTheScenarioOrWhileFalling() throws Exception {
        for (boolean falling : new boolean[]{false, true}) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                p.grant(7, true);
                assertTrue(p.energy.activate(p.use(0, 7)));

                if (falling) {
                    var actor = p.f.runtime.snapshot().actors().get(p.f.room.owner);
                    int bottom = p.f.match.definitions().map().height() + 200;
                    var move = p.energy.movement.move(0, bottom - actor.y(), actor.facing(), MatchMovementKind.MOVE);
                    assertTrue(p.energy.movement.move(move));
                    assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                    p.energy.movement.timer(p.f.timers.deadlines.getFirst().deadlineNanos());
                } else {
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var scene = MatchGuidedTestPackets.shot(0, 0, List.of(7), 0, 1);
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

    @Test
    void clearsTheCommittedGuidedTurnAfterTheMissingAcknowledgementExpires() throws Exception {
        try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
            p.grant(7, true);
            assertTrue(p.energy.activate(p.use(0, 7)));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var scene = MatchGuidedTestPackets.shot(0, 0, List.of(7), 0, 1);
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
            var timer = p.f.timers.commands.getFirst();
            p.f.runtime.confirmScenario(p.energy.movement.live.ack(0, false));
            p.f.run();
            p.f.timers.now = p.f.runtime.snapshot().deadlineNanos();
            timer.run();
            p.f.run();

            assertEquals(RoomMatchEndReason.DISCONNECTED, p.f.callbacks.reason);
            assertTrue(p.f.match.attacks().isEmpty());
            assertTrue(p.f.match.loot().inventories().isEmpty());
            assertEquals(1, p.f.owner.spends.size());
            timer.run();
            p.f.run();
            assertEquals(1, p.f.owner.spends.size());
        }
    }

    @Test
    void disconnectClearsBothUnspentAndStagedSelectionsAndANewMatchStartsEmpty() throws Exception {
        for (boolean spending : new boolean[]{false, true}) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                p.grant(7, true);
                assertTrue(p.energy.activate(p.use(0, 7)));

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

        try (var fresh = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
            assertTrue(fresh.f.runtime.snapshot().attacks().isEmpty());
            assertTrue(fresh.f.runtime.snapshot().loot().inventories().isEmpty());
        }
    }
}
