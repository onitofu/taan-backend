package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.match.model.MatchAttackSelection;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.SpentMatchItem;

class MatchPowerTest {
    @Test
    void consumesAnOwnedConfirmedCardOnceWithoutChangingEnergyAndStagesItsSpendUntilTheWorldCommit() throws Exception {
        try (var p = new MatchPowerFixture(); var logs = new LogCapture()) {
            p.grant(0, 5, true);
            var use = p.use(0, 5);
            var before = p.f.runtime.snapshot();

            assertTrue(p.energy.activate(use));
            var activated = p.f.runtime.snapshot();
            assertSame(before.world(), activated.world());
            assertEquals(0, activated.actors().get(use.context().sessionId()).energy());
            var selection = activated.attacks().get(use.context().sessionId());
            assertEquals(List.of(5), selection.selectedItems());
            assertEquals(1, selection.attackKind());
            assertEquals(65535, activated.loot().inventory(use.context().sessionId()).slots().getFirst());
            assertTrue(activated.loot().inventory(use.context().sessionId()).receipts().isEmpty());
            assertEquals(1, selection.consumedSlots().get(0).grantSequence());
            assertEquals(List.of(5, 65535, 65535), before.loot().inventory(use.context().sessionId()).slots());

            assertTrue(p.energy.activate(use));
            assertEquals(1, p.f.owner.uses.size());
            assertEquals(1, p.f.guest.uses.size());
            assertSame(activated, p.f.runtime.snapshot());
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            assertTrue(p.energy.record(p.energy.spend(List.of())));
            assertTrue(p.f.owner.spends.isEmpty());
            assertEquals(selection, p.f.runtime.snapshot().attacks().get(use.context().sessionId()));

            var shot = p.shot();
            var result = p.apply(shot);
            assertEquals(ScenarioApplicationOutcome.APPLIED, result.outcome());
            var committed = result.committed().orElseThrow();
            assertEquals(Map.of(0, 1), committed.scenario().journal().missileDefinitions());
            assertTrue(committed.changedCells() > 0);
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
            assertEquals(1, p.f.owner.spends.size());
            assertEquals(1, p.f.guest.spends.size());
            assertTrue(p.f.owner.scenarios.isEmpty());
            assertArrayEquals(shot.root(), p.f.guest.scenarios.getFirst().scenario().root());
            assertEquals(ScenarioApplicationOutcome.DUPLICATE, p.apply(shot).outcome());
            assertEquals(1, p.f.owner.spends.size());
            assertEquals(1, p.f.guest.scenarios.size());
            p.energy.acknowledge();
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
            assertTrue(p.f.runtime.items.canCloseTurn());
            assertTrue(logs.text().contains("match.power.activated"));
            assertTrue(logs.text().contains("match.power.duplicate"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));

            for (int activation = 0; activation < 2; activation++) {
                p.energy.charge();
                assertTrue(p.energy.activate(p.energy.use()));
                assertTrue(p.energy.record(p.energy.spend(List.of())));
                assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.energy.shot(true, 0)).outcome());
                p.energy.acknowledge();
            }

            assertNull(p.f.callbacks.reason);
        }
    }

    @Test
    void keepsDuplicateCardsButSelectsOnlyOneAndPreservesTheirSelectionThroughF9() throws Exception {
        try (var p = new MatchPowerFixture()) {
            for (int index = 0; index < 3; index++) {
                p.grant(0, 5, true);
                assertTrue(p.energy.activate(p.use(0, 5)));
            }

            var active = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(5, 5, 5), active.activeItems());
            assertEquals(List.of(5), active.selectedItems());
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            assertTrue(p.energy.movement.pass(p.energy.movement.pass()));
            assertEquals(active, p.f.runtime.snapshot().attacks().get(p.f.room.owner));
            var spent = List.of(new SpentMatchItem(5, 0), new SpentMatchItem(5, 255), new SpentMatchItem(5, 7));
            assertTrue(p.energy.record(p.energy.spend(spent)));
            assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.shot()).outcome());
            assertTrue(p.f.runtime.snapshot().attacks().isEmpty());
        }
    }

    @Test
    void specialWinsInEitherActivationOrderAndCanBeRechargedAndUsedAgainAfterPower() throws Exception {
        for (boolean specialFirst : new boolean[]{false, true}) {
            try (var p = new MatchPowerFixture()) {
                p.energy.charge();
                p.grant(0, 5, true);

                if (specialFirst) {
                    assertTrue(p.energy.activate(p.energy.use()));
                }

                assertTrue(p.energy.activate(p.use(0, 5)));

                if (!specialFirst) {
                    assertEquals(100, p.f.runtime.snapshot().actors().get(p.f.room.owner).energy());
                    assertTrue(p.energy.activate(p.energy.use()));
                }

                var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
                assertEquals(specialFirst ? List.of(6, 5) : List.of(5, 6), selection.activeItems());
                assertEquals(List.of(6), selection.selectedItems());
                assertEquals(2, selection.attackKind());
                assertEquals(0, p.f.runtime.snapshot().actors().get(p.f.room.owner).energy());
                assertTrue(p.energy.record(p.energy.spend(List.of(new SpentMatchItem(6, 0)))));
                assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.energy.shot(true, 10)).outcome());
                p.energy.acknowledge();
                p.energy.charge();
                assertTrue(p.energy.activate(p.energy.use()));
                assertTrue(p.energy.record(p.energy.spend(List.of())));
                assertEquals(ScenarioApplicationOutcome.APPLIED, p.apply(p.energy.shot(true, 0)).outcome());
                p.energy.acknowledge();
                assertNull(p.f.callbacks.reason);
            }
        }
    }

    @Test
    void clearsPowerOnALethalWalkingFallAndRetainsItWhenLandingAlive() throws Exception {
        for (boolean lethal : new boolean[]{false, true}) {
            try (var p = new MatchPowerFixture()) {
                p.grant(0, 5, true);
                assertTrue(p.energy.activate(p.use(0, 5)));
                var session = p.f.room.owner;
                var actor = p.f.match.actors().get(session);
                int y = lethal ? p.f.match.definitions().map().height() + 1 : actor.y() - 20;
                p.f.match.move(session, actor.x(), y, actor.facing(), p.f.timers.now);
                var fall = p.f.match.movements().get(session).fall().orElseThrow();

                p.f.match.settleMovement(fall.endsAtNanos());
                assertEquals(lethal, p.f.match.actors().get(session).dead());
                assertEquals(lethal, p.f.match.attacks().isEmpty());
            }
        }
    }

    @Test
    void usesEveryInventorySlotAndReleasesItsReceiptWithoutGrantingAnItemAgain() throws Exception {
        try (var p = new MatchPowerFixture()) {
            p.grant(0, 5, true);
            p.grant(0, 5, true);
            p.grant(0, 5, true);

            for (int index : new int[]{2, 0, 1}) {
                assertTrue(p.energy.activate(p.use(index, 5)));
                assertTrue(p.energy.activate(p.use(index, 5)));
            }

            var selection = p.f.runtime.snapshot().attacks().get(p.f.room.owner);
            assertEquals(List.of(5, 5, 5), selection.activeItems());
            assertEquals(3, selection.consumedSlots().size());
            assertEquals(3, p.f.owner.uses.size());
            assertEquals(3, p.f.runtime.snapshot().loot().grantSequence());
            assertTrue(p.f.runtime.snapshot().loot().inventory(p.f.room.owner).receipts().isEmpty());
            assertEquals(List.of(65535, 65535, 65535), p.f.runtime.snapshot().loot().inventory(p.f.room.owner).slots());
            var guestSelection = p.f.runtime.snapshot().attacks()
                .getOrDefault(p.f.room.players.get(1), MatchAttackSelection.NORMAL);
            assertEquals(MatchAttackSelection.NORMAL, guestSelection);
        }
    }
}
