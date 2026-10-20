package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MatchNativeLootRulesTest {
    @Test
    void matchesAllNativeScalarZeroWitnessesAndTheirWeightBoundaries() throws Exception {
        try (var input = getClass().getResourceAsStream("/match/native-loot-scalar0.tsv")) {
            var rows = new String(input.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1).toList();
            assertEquals(13, rows.size());

            for (var row : rows) {
                var fields = row.split("\t");
                int start = Integer.parseInt(fields[0]);
                int item = Integer.parseInt(fields[1]);
                int weight = Integer.parseInt(fields[2]);

                for (int draw = start; draw < start + weight; draw++) {
                    MatchNativeLootRules.requirePickup(item, draw);
                    MatchNativeLootRules.requirePickup(item, draw + 300);
                }

                var rejected = assertThrows(
                    ScenarioWorldException.class,
                    () -> MatchNativeLootRules.requirePickup(item == 1 ? 5 : 1, start)
                );
                assertEquals("INVALID_PICKUP_REWARD", rejected.getMessage());
            }
        }

        assertDoesNotThrow(() -> MatchNativeLootRules.requirePickup(5, Integer.MAX_VALUE));
    }

    @Test
    void rejectsDraftAndIneligibleItemsAndDrawsOutsideTheEmbeddedProducerRange() {
        for (int item : new int[]{0, 2, 3, 4, 6, 1006, 65535}) {
            assertThrows(ScenarioWorldException.class, () -> MatchNativeLootRules.requirePickup(item, 225));
        }

        for (int draw : new int[]{-1, Integer.MIN_VALUE}) {
            var rejected = assertThrows(
                ScenarioWorldException.class,
                () -> MatchNativeLootRules.requirePickup(5, draw)
            );
            assertEquals("INVALID_PICKUP_DRAW", rejected.getMessage());
        }
    }
}
