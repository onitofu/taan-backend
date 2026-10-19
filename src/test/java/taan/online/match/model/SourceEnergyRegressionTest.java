package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SourceEnergyRegressionTest {
    @Test
    void reproducesTheRecordedEnergyLedgerWithoutReinterpretingPreviouslyRejectedSpecialRequests() throws Exception {
        String csv;

        try (var input = getClass().getResourceAsStream("/match/energy-source-ledger.csv")) {
            csv = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }

        var energy = new HashMap<Integer, Integer>();
        int regressionPoints = 0;

        for (String line : csv.lines().skip(1).toList()) {
            var row = line.split(",");
            long time = Long.parseLong(row[0]);
            String type = row[1];
            int slot = Integer.parseInt(row[2]);
            int grant = Integer.parseInt(row[3]);

            if (type.equals("BOOTSTRAP")) {
                energy.put(0, Integer.parseInt(row[7]));
                energy.put(2, Integer.parseInt(row[8]));
            } else if (type.equals("SHOT")) {
                energy.compute(slot, (ignored, value) -> MatchEnergyRules.adjust(value, grant, 100));

                for (int pass = 0; pass < 2; pass++) {
                    energy.compute(
                        0,
                        (ignored, value) -> MatchEnergyRules.adjust(value, Integer.parseInt(row[4]), 100)
                    );
                    energy.compute(
                        2,
                        (ignored, value) -> MatchEnergyRules.adjust(value, Integer.parseInt(row[5]), 100)
                    );
                }
            } else if (type.equals("TURN")) {
                energy.compute(slot, (ignored, value) -> MatchEnergyRules.adjust(value, grant, 100));
            }

            assertEquals(Integer.parseInt(row[7]), energy.get(0), line);
            assertEquals(Integer.parseInt(row[8]), energy.get(2), line);

            if (time == 118592 || time == 188737) {
                assertEquals(100, energy.get(slot));
                regressionPoints++;
            }

            if (type.equals("SPECIAL") && Boolean.parseBoolean(row[6])) {
                energy.put(slot, 0);
            }
        }

        assertEquals(2, regressionPoints);
    }
}
