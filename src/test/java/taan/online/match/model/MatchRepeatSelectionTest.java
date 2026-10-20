package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.match.service.ScenarioTestWorld;

class MatchRepeatSelectionTest {
    @Test
    void preservesNativeCategoryLimitsPrioritiesAndMultiplicityIndependentlyOfActivationOrder() {
        for (var items : List.of(List.of(1, 2, 1, 2, 5, 6), List.of(6, 5, 2, 1, 2, 1))) {
            var selection = new MatchAttackSelection(items, Map.of());
            assertEquals(items, selection.activeItems());
            assertEquals(List.of(6, 2, 2, 1), selection.selectedItems());
            assertEquals(6, selection.shotLimit());
            assertEquals(2, selection.attackKind());
        }

        assertEquals(1, MatchAttackSelection.NORMAL.shotLimit());
        assertEquals(4, new MatchAttackSelection(List.of(1, 1, 1, 1), Map.of()).shotLimit());
        assertEquals(7, new MatchAttackSelection(List.of(2, 2, 2, 2), Map.of()).shotLimit());
        assertEquals(0, new MatchAttackSelection(List.of(1, 2), Map.of()).attackKind());
        assertEquals(1, new MatchAttackSelection(List.of(1, 5), Map.of()).attackKind());
    }

    @Test
    void consumesEachConfirmedRepeatCardWithoutChangingWorldOrEnergy() throws Exception {
        try (var world = new ScenarioTestWorld()) {
            var snapshot = world.snapshot();
            var member = snapshot.participants().getFirst();

            for (int item : new int[]{1, 2}) {
                var loot = MatchLootState.empty().offered(new MatchBonusOffer(
                    member.sessionId(),
                    member.admissionId(),
                    1,
                    0,
                    false
                )).granted(member.sessionId(), item);
                assertTrue(MatchItemSelectionRules.activate(
                    member,
                    snapshot.world(),
                    loot,
                    MatchAttackSelection.NORMAL,
                    0,
                    item
                ).isEmpty());
                var confirmed = loot.confirmed(member.sessionId(), member.admissionId(), 0, item);
                var applied = MatchItemSelectionRules.activate(
                    member,
                    snapshot.world(),
                    confirmed,
                    MatchAttackSelection.NORMAL,
                    0,
                    item
                ).orElseThrow();
                assertEquals(snapshot.world(), applied.world());
                assertEquals(BattleInventory.EMPTY, applied.loot().inventory(member.sessionId()).slots().getFirst());
                assertEquals(List.of(item), applied.selection().activeItems());
                assertTrue(applied.selection().duplicate(0, item));
                assertFalse(applied.selection().duplicate(0, item == 1 ? 2 : 1));
            }
        }
    }
}
