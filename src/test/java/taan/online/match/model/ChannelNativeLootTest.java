package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ChannelNativeLootTest {
    @Test
    void matchesEveryNativeCandidateAndEveryDrawForAllOneHundredAndOneSupportedChannelValues() throws Exception {
        try (var input = getClass().getResourceAsStream("/match/loot-picker/native-candidates.tsv")) {
            var rows = new String(input.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1).toList();
            var totals = new HashMap<Integer, Integer>();
            var scalars = new HashSet<Integer>();
            assertEquals(1406, rows.size());

            for (var row : rows) {
                var fields = row.split("\t");
                int scalar = Integer.parseInt(fields[0]);
                int draw = Integer.parseInt(fields[1]);
                int item = Integer.parseInt(fields[2]);
                int weight = Integer.parseInt(fields[3]);
                assertEquals(totals.getOrDefault(scalar, 0), draw);
                totals.merge(scalar, weight, Integer::sum);
                scalars.add(scalar);
                assertTrue(MatchNativeLootRules.eligible(item, scalar));

                for (int value = draw; value < draw + weight; value++) {
                    MatchNativeLootRules.requirePickup(item, value, scalar);
                }

                assertThrows(
                    ScenarioWorldException.class,
                    () -> MatchNativeLootRules.requirePickup(item == 1 ? 5 : 1, draw, scalar)
                );
            }

            assertEquals(101, scalars.size());
            assertEquals(300, totals.get(0));
            assertEquals(418, totals.get(70));
            assertEquals(477, totals.get(89));

            for (var row : rows) {
                var fields = row.split("\t");
                int scalar = Integer.parseInt(fields[0]);
                int draw = Integer.parseInt(fields[1]);
                int item = Integer.parseInt(fields[2]);
                MatchNativeLootRules.requirePickup(item, draw + totals.get(scalar), scalar);
            }
        }
    }

    @Test
    void rejectsUnsupportedValuesAndItemsBeforeTheirNativeThresholds() {
        assertFalse(MatchNativeLootRules.eligible(2, 69));
        assertTrue(MatchNativeLootRules.eligible(2, 70));
        assertFalse(MatchNativeLootRules.eligible(3, 49));
        assertTrue(MatchNativeLootRules.eligible(3, 50));
        assertFalse(MatchNativeLootRules.eligible(4, 89));

        for (int scalar : new int[]{-1, 101, 65535, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertFalse(MatchNativeLootRules.eligible(1, scalar));
            assertEquals(
                "INVALID_PICKER_SCALAR",
                assertThrows(
                    ScenarioWorldException.class,
                    () -> MatchNativeLootRules.requirePickup(1, 0, scalar)
                ).getMessage()
            );
        }
    }
}
