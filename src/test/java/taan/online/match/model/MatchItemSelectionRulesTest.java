package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.match.service.ScenarioTestWorld;

class MatchItemSelectionRulesTest {
    @Test
    void preservesRepeatedCandidatesAndSelectsTheNativeCategoryWinnerInBothOrders() {
        assertEquals(List.of(), MatchAttackSelection.NORMAL.selectedItems());
        assertEquals(0, MatchAttackSelection.NORMAL.attackKind());

        for (var items : List.of(List.of(5), List.of(5, 5, 5))) {
            var selection = new MatchAttackSelection(items, Map.of());
            assertEquals(items, selection.activeItems());
            assertEquals(List.of(5), selection.selectedItems());
            assertEquals(1, selection.attackKind());
            assertEquals(List.of(6), selection.activatedSpecial().selectedItems());
        }

        for (var items : List.of(List.of(5, 6), List.of(6, 5), List.of(6))) {
            var selection = new MatchAttackSelection(items, Map.of());
            assertEquals(List.of(6), selection.selectedItems());
            assertEquals(2, selection.attackKind());
        }

        assertThrows(IllegalArgumentException.class, () -> new MatchAttackSelection(List.of(7), Map.of()));
    }

    @Test
    void calculatesActivationWithoutMutatingItsInputsAndRequiresTheConfirmedOwnedReceipt() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var snapshot = f.snapshot();
            var member = snapshot.participants().getFirst();
            var world = snapshot.world();
            var normal = MatchAttackSelection.NORMAL;
            var loot = loot(member, member.admissionId(), 5, true);
            var applied = MatchItemSelectionRules.activate(member, world, loot, normal, 0, 5)
                .orElseThrow();

            assertSame(world, applied.world());
            assertEquals(List.of(5, 65535, 65535), loot.inventory(member.sessionId()).slots());
            assertEquals(List.of(65535, 65535, 65535), applied.loot().inventory(member.sessionId()).slots());
            assertEquals(1, applied.selection().consumedSlots().get(0).grantSequence());
            assertTrue(applied.selection().duplicate(0, 5));
            assertFalse(applied.selection().duplicate(1, 5));
            assertFalse(applied.selection().duplicate(0, 7));

            for (var invalid : List.of(
                MatchLootState.empty(),
                loot(member, member.admissionId(), 5, false),
                loot(member, UUID.randomUUID(), 5, true),
                loot(member, member.admissionId(), 7, true)
            )) {
                assertTrue(MatchItemSelectionRules.activate(member, world, invalid, normal, 0, 5).isEmpty());
            }

            for (int index : new int[]{-1, 3, 255}) {
                assertTrue(MatchItemSelectionRules.activate(member, world, loot, normal, index, 5).isEmpty());
            }

            assertTrue(MatchItemSelectionRules.activate(member, world, loot, normal, 0, 7).isEmpty());
            assertTrue(MatchItemSelectionRules.activate(member, world, loot, normal, 255, 6).isEmpty());
            var full = world.energy(member.sessionId(), member.maxEnergy());
            assertTrue(MatchItemSelectionRules.activate(member, full, loot, normal, 0, 6).isEmpty());
            var selected = applied.selection();
            var special = MatchItemSelectionRules.activate(member, full, loot, selected, 255, 6).orElseThrow();
            assertEquals(0, special.world().actors().get(member.sessionId()).energy());
            assertSame(loot, special.loot());
            assertEquals(List.of(5, 6), special.selection().activeItems());
        }
    }

    @Test
    void copiesActivationSourcesAndBoundsAccumulationAcrossUnspentTurns() throws Exception {
        try (var f = new ScenarioTestWorld()) {
            var member = f.snapshot().participants().getFirst();
            var initialLoot = loot(member, member.admissionId(), 5, true);
            var receipt = initialLoot.inventory(member.sessionId()).receipts().getFirst();
            var items = new ArrayList<>(List.of(5));
            var sources = new HashMap<Integer, MatchItemReceipt>();
            sources.put(0, receipt);
            var selected = new MatchAttackSelection(items, sources);
            items.clear();
            sources.clear();

            assertEquals(List.of(5), selected.activeItems());
            assertEquals(receipt, selected.consumedSlots().get(0));
            assertThrows(UnsupportedOperationException.class, () -> selected.activeItems().clear());
            assertThrows(UnsupportedOperationException.class, () -> selected.consumedSlots().clear());
            var itemsAtLimit = Collections.nCopies(MatchAttackSelection.MAX_ACTIVATIONS, 5);
            var limit = new MatchAttackSelection(itemsAtLimit, Map.of());
            assertThrows(ScenarioWorldException.class, limit::activatedSpecial);
            assertThrows(ScenarioWorldException.class, () -> limit.activated(0, receipt));
        }
    }

    private MatchLootState loot(MatchParticipant member, UUID admission, int item, boolean confirmed) {
        var loot = MatchLootState.empty().offered(new MatchBonusOffer(member.sessionId(), admission, 1, 0, false))
            .granted(member.sessionId(), item);
        return confirmed ? loot.confirmed(member.sessionId(), admission, 0, item) : loot;
    }
}
