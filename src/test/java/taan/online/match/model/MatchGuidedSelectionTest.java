package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchGuidedSelectionTest {
    @Test
    void selectsGuidanceLastInItsOwnCategoryAndRetainsEveryConsumedSource() {
        var items = List.of(7, 3, 7, 5, 1005, 1050, 1, 2, 2, 2, 6);
        var selection = new MatchAttackSelection(items, Map.of());

        assertTrue(MatchItemSelectionRules.supports(7));
        assertEquals(items, selection.activeItems());
        assertEquals(List.of(6, 2, 2, 2, 3, 1050, 1005, 7), selection.selectedItems());
        assertEquals(21, selection.shotLimit());
        assertEquals(2, selection.attackKind());
        assertEquals(List.of(7), new MatchAttackSelection(List.of(7, 7, 7), Map.of()).selectedItems());
        assertEquals(1, new MatchAttackSelection(List.of(7), Map.of()).shotLimit());
        assertEquals(0, new MatchAttackSelection(List.of(7), Map.of()).attackKind());
    }
}
