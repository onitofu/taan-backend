package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class ScenarioTerrainOracleTest {
    @Test
    void matchesThirtyIndependentCatalogMaskExecutionsAcrossAllJungleLayers() throws Exception {
        String data;

        try (var input = getClass().getResourceAsStream("/match/terrain-crater-oracle.csv")) {
            data = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var definitions = MatchFixtures.definitions();

        for (String line : data.lines().skip(1).toList()) {
            String[] row = line.split(",");
            var draft = new ScenarioTerrainDraft(definitions.map().terrainLayers());
            draft.explode(
                definitions.missiles().get(0),
                Integer.parseInt(row[0]),
                Integer.parseInt(row[1]),
                Float.parseFloat(row[2])
            );
            var result = draft.freeze();
            assertEquals(Integer.parseInt(row[3]), draft.changedCells(), line);

            for (int layer = 0; layer < result.size(); layer++) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(result.get(layer).cells());
                String hash = HexFormat.of().formatHex(digest);
                assertEquals(row[4 + layer], hash, line);
            }
        }
    }
}
