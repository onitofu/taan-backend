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
import taan.online.MatchDamageUpTestPackets;
import taan.online.TestFixtures;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;

class MatchDamageUpTest {
    @Test
    void consumesEveryDamageCardFromAllThreeSlotsAndAppliesCanonicalDamageWithoutAnotherMultiplier() throws Exception {
        for (int item : MatchDamageUpTestPackets.ITEMS) {
            try (var p = new MatchRepeatFixture(MatchDamageUpTestPackets.POLICY); var logs = new LogCapture()) {
                p.grant(item, true);
                p.grant(item, true);
                p.grant(item, true);
                var before = p.f.runtime.snapshot();

                for (int index : new int[]{2, 0, 1}) {
                    var use = p.use(index, item);
                    assertTrue(p.energy.activate(use));
                    assertTrue(p.energy.activate(use));
                }

                var selected = p.f.runtime.snapshot();
                assertSame(before.world(), selected.world());
                assertEquals(List.of(item, item, item), selected.attacks().get(p.f.room.owner).selectedItems());
                assertEquals(3, selected.attacks().get(p.f.room.owner).consumedSlots().size());
                assertEquals(3, p.f.owner.uses.size());
                assertEquals(3, p.f.guest.uses.size());
                assertTrue(selected.loot().inventory(p.f.room.owner).receipts().isEmpty());
                var spent = List.of(
                    new SpentMatchItem(item, 0),
                    new SpentMatchItem(item, 1),
                    new SpentMatchItem(item, 2)
                );
                var spend = p.energy.spend(spent);
                assertTrue(p.energy.record(spend));
                assertTrue(p.energy.record(spend));
                assertEquals(selected.attacks(), p.f.runtime.snapshot().attacks());
                var scene = p.shot(1);

                var result = p.apply(scene);
                assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
                var committed = p.f.runtime.snapshot();
                assertEquals(before.world().revision() + 1, committed.world().revision());
                var victim = p.f.room.players.get(1);
                assertEquals(before.actors().get(victim).hp() - 10, committed.actors().get(victim).hp());
                assertEquals(2, committed.actors().get(p.f.room.owner).energy());
                assertEquals(4, committed.actors().get(victim).energy());
                assertTrue(committed.attacks().isEmpty());
                assertEquals(1, p.f.owner.spends.size());
                assertEquals(1, p.f.guest.spends.size());
                assertTrue(p.f.owner.scenarios.isEmpty());
                assertArrayEquals(new ScenarioCodec().encode(scene), p.f.guest.scenarios.getFirst().scenario().root());
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
                assertTrue(logs.text().contains("match.damageup.activated"));
                assertTrue(logs.text().contains("match.damageup.duplicate"));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            }
        }
    }

    @Test
    void combinesDamageCardsWithBothRepeatItemsPowerAndSpecialInEitherActivationOrder() throws Exception {
        for (int item : MatchDamageUpTestPackets.ITEMS) {
            for (int repeat : new int[]{1, 2}) {
                for (int attack : new int[]{5, 6}) {
                    for (boolean damageFirst : new boolean[]{false, true}) {
                        try (var p = new MatchRepeatFixture(MatchDamageUpTestPackets.POLICY)) {
                            if (attack == 6) {
                                p.energy.charge();
                            }

                            var order = damageFirst ? new int[]{item, repeat, attack} : new int[]{attack, repeat, item};

                            for (int selected : order) {
                                if (selected == 6) {
                                    assertTrue(p.energy.activate(p.energy.use()));
                                } else {
                                    p.grant(selected, true);
                                    assertTrue(p.energy.activate(p.use(0, selected)));
                                }
                            }

                            var before = p.f.runtime.snapshot();
                            var selected = before.attacks().get(p.f.room.owner);
                            assertEquals(List.of(attack, repeat, item), selected.selectedItems());
                            assertEquals(repeat + 1, selected.shotLimit());
                            assertEquals(attack == 6 ? 2 : 1, selected.attackKind());
                            assertTrue(p.energy.record(p.energy.spend(List.of())));
                            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.shot(repeat + 1)).outcome());
                            var victim = p.f.room.players.get(1);
                            assertEquals(
                                before.actors().get(victim).hp() - 10 * (repeat + 1),
                                p.f.runtime.snapshot().actors().get(victim).hp()
                            );
                            p.energy.acknowledge();
                            assertNull(p.f.callbacks.reason);
                        }
                    }
                }
            }
        }
    }

    @Test
    void retainsCandidatesAcrossF9AndCapsOnlyTheSelectedDamageCategory() throws Exception {
        try (var p = new MatchRepeatFixture(MatchDamageUpTestPackets.POLICY)) {
            for (int item : new int[]{1005, 1050, 1010, 1050, 1045}) {
                p.grant(item, true);
                assertTrue(p.energy.activate(p.use(0, item)));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
                assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            }

            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(1005, 1050, 1010, 1050, 1045), selection.activeItems());
            assertEquals(List.of(1050, 1050, 1045, 1010), selection.selectedItems());
            assertEquals(1, selection.shotLimit());
            assertEquals(0, selection.attackKind());
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.shot(1)).outcome());
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
        }
    }
}
