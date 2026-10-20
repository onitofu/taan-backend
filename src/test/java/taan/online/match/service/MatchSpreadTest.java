package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchSpreadTestPackets;
import taan.online.TestFixtures;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ScenarioApplicationOutcome;

class MatchSpreadTest {
    @Test
    void commitsThreeToTwentyOneInterleavedProjectilesOnceForBothFacingsAndAdvancesOnlyOneTurn() throws Exception {
        for (int facing : new int[]{0, 1}) {
            for (var items : List.of(List.of(3), List.of(1, 3), List.of(2, 3), List.of(2, 2, 2, 3))) {
                try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY); var logs = new LogCapture()) {
                    assertTrue(p.energy.movement.move(p.energy.movement.move(0, 0, facing, MatchMovementKind.SET_POS)));

                    for (int item : items) {
                        p.grant(item, true);
                        var use = p.use(0, item);
                        assertTrue(p.energy.activate(use));
                        assertTrue(p.energy.activate(use));
                    }

                    int launches = 1 + items.stream().filter(item -> item != 3).mapToInt(Integer::intValue).sum();
                    var before = p.f.runtime.snapshot();
                    var selection = before.attacks().get(p.f.room.owner);
                    assertEquals(launches * 3, selection.shotLimit());
                    assertEquals(items.size(), p.f.owner.uses.size());
                    assertEquals(items.size(), p.f.guest.uses.size());
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var scene = MatchSpreadTestPackets.shot(0, facing, selection.selectedItems(), 0, launches, .5f);
                    byte[] root = new ScenarioCodec().encode(scene);

                    var result = p.apply(scene);
                    assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                    var journal = result.committed().orElseThrow().scenario().journal();
                    assertEquals(launches * 3, journal.missileDefinitions().size());
                    var committed = p.f.runtime.snapshot();
                    var victim = p.f.room.players.get(1);
                    assertEquals(before.actors().get(victim).hp() - launches * 30, committed.actors().get(victim).hp());
                    assertEquals(
                        before.actors().get(p.f.room.owner).energy() + 2,
                        committed.actors().get(p.f.room.owner).energy()
                    );
                    assertEquals(1, p.f.owner.spends.size());
                    assertEquals(1, p.f.guest.spends.size());
                    assertTrue(p.f.owner.scenarios.isEmpty());
                    assertArrayEquals(root, p.f.guest.scenarios.getFirst().scenario().root());
                    assertTrue(committed.attacks().isEmpty());
                    assertEquals(ScenarioApplicationOutcome.DUPLICATE, p.apply(scene).outcome());
                    assertSame(committed, p.f.runtime.snapshot());
                    var ownerAck = p.energy.movement.live.ack(0, false);
                    var guestAck = p.energy.movement.live.ack(1, false);

                    p.energy.acknowledge();
                    var next = p.f.runtime.snapshot();
                    assertEquals(before.turn().revision() + 1, next.turn().revision());
                    p.f.runtime.confirmScenario(ownerAck);
                    p.f.runtime.confirmScenario(guestAck);
                    p.f.run();
                    assertSame(next, p.f.runtime.snapshot());
                    assertNull(p.f.callbacks.reason);
                    assertTrue(logs.text().contains("match.spread.activated"));
                    assertTrue(logs.text().contains("match.spread.duplicate"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                }
            }
        }
    }

    @Test
    void supportsPowerSpecialAndDamageUpTogetherWithBothRepeatCards() throws Exception {
        for (int attack : new int[]{5, 6}) {
            for (int repeat : new int[]{1, 2}) {
                for (boolean spreadFirst : new boolean[]{false, true}) {
                    try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
                        if (attack == 6) {
                            p.energy.charge();
                        }

                        var items = spreadFirst ? List.of(3, repeat, attack, 1050) : List.of(1050, attack, repeat, 3);

                        for (int item : items) {
                            if (item == 6) {
                                assertTrue(p.energy.activate(p.energy.use()));
                            } else {
                                p.grant(item, true);
                                assertTrue(p.energy.activate(p.use(0, item)));
                            }
                        }

                        var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
                        assertEquals(List.of(attack, repeat, 3, 1050), selection.selectedItems());
                        int kind = attack == 6 ? 2 : 1;
                        assertEquals(kind, selection.attackKind());
                        assertTrue(p.energy.record(p.energy.spend(List.of())));
                        var scene = MatchSpreadTestPackets.shot(0, 0, selection.selectedItems(), kind, repeat + 1, .5f);
                        var result = p.apply(scene);
                        assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                        assertTrue(result.committed().orElseThrow().scenario().journal().missileDefinitions()
                            .values().stream().allMatch(value -> value == kind));
                        p.energy.acknowledge();
                        assertNull(p.f.callbacks.reason);

                        if (attack == 6) {
                            p.energy.charge();
                            assertTrue(p.energy.activate(p.energy.use()));
                        }
                    }
                }
            }
        }
    }

    @Test
    void acceptsSyntheticPowerEdgesIncludingTheUnclampedStrongerSide() throws Exception {
        for (float power : new float[]{0, 1}) {
            for (int facing : new int[]{0, 1}) {
                try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
                    assertTrue(p.energy.movement.move(p.energy.movement.move(0, 0, facing, MatchMovementKind.SET_POS)));
                    p.grant(3, true);
                    assertTrue(p.energy.activate(p.use(0, 3)));
                    assertTrue(p.energy.record(p.energy.spend(List.of())));
                    var scene = MatchSpreadTestPackets.shot(0, facing, List.of(3), 0, 1, power);
                    var fires = scene.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
                        .map(ScenarioFireCommand.class::cast).toList();
                    double[] strengths = power == 0 ? new double[]{0, 0, .03} : new double[]{1, .97, 1.03};
                    double[] angles = facing == 0 ? new double[]{45, 41, 49} : new double[]{135, 139, 131};

                    for (int index = 0; index < fires.size(); index++) {
                        var velocity = fires.get(index).velocity();
                        assertEquals(strengths[index], Math.hypot(velocity.x(), velocity.y()) / 1000, .000001);

                        if (strengths[index] > 0) {
                            assertEquals(angles[index], Math.toDegrees(Math.atan2(velocity.y(), velocity.x())), .00001);
                        }
                    }

                    assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
                }
            }
        }
    }

    @Test
    void duplicateSpreadCardsRetainTheirSourcesButProduceOnlyOneSpreadAndCanSurviveF9() throws Exception {
        try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
            for (int count = 0; count < 3; count++) {
                p.grant(3, true);
                assertTrue(p.energy.activate(p.use(0, 3)));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            }

            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(3, 3, 3), selection.activeItems());
            assertEquals(List.of(3), selection.selectedItems());
            assertEquals(3, selection.shotLimit());
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var scene = MatchSpreadTestPackets.shot(0, 0, List.of(3), 0, 1, .5f);
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
        }
    }

    @Test
    void acceptsOnlyTheAvailableProjectilesWhenTheShooterDiesBeforeLaterLaunches() throws Exception {
        try (var p = new MatchRepeatFixture(MatchSpreadTestPackets.POLICY)) {
            p.grant(2, true);
            assertTrue(p.energy.activate(p.use(0, 2)));
            p.grant(3, true);
            assertTrue(p.energy.activate(p.use(0, 3)));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            var scene = MatchSpreadTestPackets.shot(0, 0, selection.selectedItems(), 0, 3, .5f);
            var commands = new ArrayList<>(scene.scenario().commands());
            commands.removeIf(command -> command instanceof ScenarioFireCommand fire && fire.header().tick() > 700
                || command instanceof ScenarioWeaponCommand event
                    && event.weapon().shotTick() > 700
                || command.header().kind() == 4 && command.header().tick() > 2200);
            commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(0, 5, 2300, 0)));
            scene = MatchRepeatTestPackets.commands(scene, selection.selectedItems(), 0, commands);

            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
            assertTrue(p.f.runtime.snapshot().actors().get(p.f.room.owner).dead());
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
        }
    }
}
