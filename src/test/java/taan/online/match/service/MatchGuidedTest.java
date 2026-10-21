package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchGuidedTestPackets;
import taan.online.MatchRepeatTestPackets;
import taan.online.TestFixtures;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchMovementKind;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.ScenarioGuidedClaim;
import taan.online.match.service.model.SpentMatchItem;
import taan.online.room.service.model.RoomMatchEndReason;

class MatchGuidedTest {
    @Test
    void appliesGuidanceOnceForEveryAuthorizedProjectileAndWaitsForBothAcknowledgements() throws Exception {
        for (int facing : new int[]{0, 1}) {
            for (var items : List.of(List.of(7), List.of(1, 7), List.of(2, 3, 7))) {
                try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY); var logs = new LogCapture()) {
                    assertTrue(p.energy.movement.move(p.energy.movement.move(0, 0, facing, MatchMovementKind.SET_POS)));

                    for (int item : items) {
                        p.grant(item, true);
                        var use = p.use(0, item);
                        assertTrue(p.energy.activate(use));
                        assertTrue(p.energy.activate(use));
                    }

                    var before = p.f.runtime.snapshot();
                    var selection = before.attacks().get(p.f.room.owner);
                    int launches = items.contains(2) ? 3 : items.contains(1) ? 2 : 1;
                    var scene = MatchGuidedTestPackets.shot(0, facing, selection.selectedItems(), 0, launches);
                    assertTrue(p.energy.record(p.energy.spend(List.of(new SpentMatchItem(7, 255)))));

                    var result = p.apply(scene);
                    assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                    var journal = result.committed().orElseThrow().scenario().journal();
                    assertEquals(selection.shotLimit(), journal.missileDefinitions().size());
                    assertEquals(
                        selection.shotLimit(),
                        journal.events().stream().filter(ScenarioGuidedClaim.class::isInstance).count()
                    );
                    var committed = p.f.runtime.snapshot();
                    var victim = p.f.room.players.get(1);
                    assertEquals(
                        before.actors().get(victim).hp() - selection.shotLimit() * 10,
                        committed.actors().get(victim).hp()
                    );
                    assertEquals(before.world().revision() + 1, committed.world().revision());
                    assertEquals(1, p.f.owner.spends.size());
                    assertEquals(1, p.f.guest.spends.size());
                    assertArrayEquals(
                        new ScenarioCodec().encode(scene),
                        p.f.guest.scenarios.getFirst().scenario().root()
                    );
                    assertTrue(p.f.owner.scenarios.isEmpty());
                    assertTrue(committed.attacks().isEmpty());
                    assertEquals(ScenarioApplicationOutcome.DUPLICATE, p.apply(scene).outcome());
                    assertSame(committed, p.f.runtime.snapshot());

                    var first = p.f.runtime.confirmScenario(p.energy.movement.live.ack(0, false));
                    p.f.run();
                    assertTrue(first.join());
                    assertEquals(before.turn().revision(), p.f.runtime.snapshot().turn().revision());
                    var last = p.f.runtime.confirmScenario(p.energy.movement.live.ack(1, false));
                    p.f.run();
                    assertTrue(last.join());
                    assertEquals(before.turn().revision() + 1, p.f.runtime.snapshot().turn().revision());
                    assertNull(p.f.callbacks.reason);
                    assertTrue(logs.text().contains("match.guided.activated"));
                    assertTrue(logs.text().contains("match.guided.duplicate"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                    assertFalse(logs.text().contains(TestFixtures.LOGIN));
                    assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                    assertFalse(logs.text().contains("ScenarioGuided"));
                    assertFalse(logs.text().contains("targetX"));
                }
            }
        }
    }

    @Test
    void combinesGuidanceWithAttackSelectionDamageCardsRepeatsAndSpreadInEitherActivationOrder() throws Exception {
        for (int attack : new int[]{5, 6}) {
            for (int repeat : new int[]{1, 2}) {
                for (int damage = 1005; damage <= 1050; damage += 5) {
                    for (boolean first : new boolean[]{false, true}) {
                        try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                            if (attack == 6) {
                                p.energy.charge();
                            }

                            var items = first ? List.of(7, 3, damage, repeat, attack)
                                : List.of(attack, repeat, damage, 3, 7);

                            for (int item : items) {
                                if (item == 6) {
                                    assertTrue(p.energy.activate(p.energy.use()));
                                } else {
                                    p.grant(item, true);
                                    assertTrue(p.energy.activate(p.use(0, item)));
                                }
                            }

                            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
                            assertEquals(List.of(attack, repeat, 3, damage, 7), selection.selectedItems());
                            assertTrue(p.energy.record(p.energy.spend(List.of())));
                            var scene = MatchGuidedTestPackets.shot(
                                0,
                                0,
                                selection.selectedItems(),
                                selection.attackKind(),
                                repeat + 1
                            );
                            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(scene).outcome());
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
    }

    @Test
    void acceptsGuidedContactDescriptorsAndOpaqueRetargetPayloadWithoutChangingReportedDamage() throws Exception {
        for (int bits : new int[]{0x7fc01234, 0x7fa12345, 0x7f800000, 0xff800000}) {
            try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
                p.grant(7, true);
                assertTrue(p.energy.activate(p.use(0, 7)));
                assertTrue(p.energy.record(p.energy.spend(List.of())));
                var scene = MatchGuidedTestPackets.shot(0, 0, List.of(7), 0, 1);
                var target = new ScenarioIntPoint(Integer.MIN_VALUE, Integer.MAX_VALUE);
                var commands = scene.scenario().commands().stream().map(event -> {
                    if (event instanceof ScenarioWeaponCommand contact) {
                        return new ScenarioGuidedCommand(contact.header(), contact.weapon(), target);
                    }

                    if (event instanceof ScenarioGuidedCommand guided) {
                        var w = guided.weapon();
                        return new ScenarioGuidedCommand(
                            guided.header(),
                            new ScenarioWeaponFields(w.sequence(), w.variant(), w.position(), w.shotTick(), bits, 254),
                            target
                        );
                    }

                    return event;
                }).toList();
                scene = MatchRepeatTestPackets.commands(scene, List.of(7), 0, commands);
                byte[] root = new ScenarioCodec().encode(scene);
                assertEquals(scene, new ScenarioCodec().decode(root));

                var result = p.apply(scene);
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                assertArrayEquals(root, p.f.guest.scenarios.getFirst().scenario().root());
                assertEquals(990, p.f.runtime.snapshot().actors().get(p.f.room.players.get(1)).hp());
            }
        }
    }

    @Test
    void retainsAllConsumedGuidedCardsAcrossPassesButSelectsOnlyOneAndAllowsNoRetargetEvent() throws Exception {
        try (var p = new MatchRepeatFixture(MatchGuidedTestPackets.POLICY)) {
            for (int count = 0; count < 3; count++) {
                p.grant(7, true);
                assertTrue(p.energy.activate(p.use(0, 7)));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            }

            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(7, 7, 7), selection.activeItems());
            assertEquals(List.of(7), selection.selectedItems());
            var spend = p.energy.spend(List.of(
                new SpentMatchItem(7, 0),
                new SpentMatchItem(7, 1),
                new SpentMatchItem(7, 255)
            ));
            assertTrue(p.energy.record(spend));
            assertTrue(p.energy.record(spend));
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.shot(1)).outcome());
            assertEquals(1, p.f.owner.spends.size());
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
}
