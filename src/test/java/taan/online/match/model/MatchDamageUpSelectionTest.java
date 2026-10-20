package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import taan.online.MatchDamageUpTestPackets;

class MatchDamageUpSelectionTest {
    @Test
    void selectsOnlyTheTenNativeDefinitionsInTheirRecordedPriorityOrder() throws Exception {
        List<String[]> rows;

        try (var input = getClass().getResourceAsStream("/match/damage-up/native-damage-up-definitions.tsv")) {
            rows = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1)
                .map(line -> line.split("\t")).toList();
        }

        assertEquals(MatchDamageUpTestPackets.ITEMS, rows.stream().map(row -> Integer.parseInt(row[0])).toList());

        for (var row : rows) {
            int item = Integer.parseInt(row[0]);
            assertEquals("3", row[1]);
            assertEquals("2", row[2]);
            assertTrue(MatchItemSelectionRules.supports(item));
            assertTrue(MatchItemSelectionRules.isDamageUp(item));
            var selected = new MatchAttackSelection(List.of(item), Map.of());
            assertEquals(List.of(item), selected.selectedItems());
            assertEquals(0, selected.attackKind());
            assertEquals(1, selected.shotLimit());

            for (var other : rows) {
                int otherId = Integer.parseInt(other[0]);
                var candidates = new ArrayList<>(List.of(row, other));
                candidates.sort(Comparator.comparingInt(value -> Integer.parseInt(value[3])));
                var expected = candidates.stream().map(value -> Integer.parseInt(value[0])).toList();
                assertEquals(expected, MatchItemSelectionRules.selected(List.of(item, otherId)));
            }
        }

        for (int item : new int[]{-1, 0, 4, 1000, 1001, 1006, 1049, 1051, 1055, 65535}) {
            assertFalse(MatchItemSelectionRules.supports(item));
            assertFalse(MatchItemSelectionRules.isDamageUp(item));
            assertThrows(IllegalArgumentException.class, () -> new MatchAttackSelection(List.of(item), Map.of()));
        }
    }

    @Test
    void keepsFourDamageCardsIncludingDuplicatesAndPreservesIndependentAttackAndRepeatCategories() {
        var raw = List.of(1005, 1050, 1025, 1050, 1030, 1, 2, 1, 2, 5, 6);
        var selection = new MatchAttackSelection(raw, Map.of());
        assertEquals(raw, selection.activeItems());
        assertEquals(List.of(6, 2, 2, 1, 1050, 1050, 1030, 1025), selection.selectedItems());
        assertEquals(6, selection.shotLimit());
        assertEquals(2, selection.attackKind());
        var repeated = MatchItemSelectionRules.selected(List.of(1005, 1005, 1005, 1005, 1005));
        assertEquals(List.of(1005, 1005, 1005, 1005), repeated);
        assertEquals(List.of(5, 1050), MatchItemSelectionRules.selected(List.of(1050, 5)));
        assertEquals(List.of(6, 1050), MatchItemSelectionRules.selected(List.of(1050, 6)));
    }
}
