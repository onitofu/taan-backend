package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchSpreadSelectionTest {
    @Test
    void capsTheSpreadCategoryAtOneWithoutChangingOtherCategoriesOrDroppingRawCandidates() {
        var items = List.of(3, 3, 1005, 1050, 1, 2, 2, 2, 5, 6);
        var selected = new MatchAttackSelection(items, Map.of());
        assertTrue(MatchItemSelectionRules.supports(3));
        assertEquals(items, selected.activeItems());
        assertEquals(List.of(6, 2, 2, 2, 3, 1050, 1005), selected.selectedItems());
        assertEquals(21, selected.shotLimit());
        assertEquals(2, selected.attackKind());
        assertEquals(3, new MatchAttackSelection(List.of(3, 3, 3), Map.of()).shotLimit());
        assertEquals(6, new MatchAttackSelection(List.of(3, 1), Map.of()).shotLimit());
        assertEquals(9, new MatchAttackSelection(List.of(3, 2), Map.of()).shotLimit());
        assertEquals(0, new MatchAttackSelection(List.of(3), Map.of()).attackKind());
    }
}
