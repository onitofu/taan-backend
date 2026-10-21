package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MatchLootItemDefinitionTest {
    @Test
    void interpolatesIntegerWeightsWithinInclusiveResourceBounds() {
        var plusTwo = new MatchLootItemDefinition(2, 70, 100, 10, 40);
        assertEquals(0, plusTwo.weight(69));
        assertEquals(10, plusTwo.weight(70));
        assertEquals(29, plusTwo.weight(89));
        assertEquals(40, plusTwo.weight(100));
        assertEquals(0, plusTwo.weight(101));
        var spread = new MatchLootItemDefinition(3, 50, 100, 10, 40);
        assertEquals(22, spread.weight(70));
        assertEquals(33, spread.weight(89));
    }

    @Test
    void rejectsInvalidIdentitiesIntervalsAndNonpositiveWeights() {
        for (int[] values : new int[][]{
            {0, 0, 100, 1, 1},
            {65536, 0, 100, 1, 1},
            {1, -1, 100, 1, 1},
            {1, 0, 101, 1, 1},
            {1, 50, 50, 1, 1},
            {1, 60, 50, 1, 1},
            {1, 0, 100, 0, 1},
            {1, 0, 100, 1, 0}
        }) {
            assertThrows(IllegalArgumentException.class, () -> new MatchLootItemDefinition(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4]
            ));
        }
    }
}
