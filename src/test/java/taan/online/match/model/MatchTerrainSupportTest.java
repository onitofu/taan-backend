package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchTerrainSupportTest {
    @Test
    void matchesIndependentResearchExecutionOnTwoThousandHeightProfiles() throws Exception {
        String data;

        try (var input = getClass().getResourceAsStream("/match/terrain-support-oracle.csv")) {
            data = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String line : data.lines().skip(1).toList()) {
            String[] fields = line.split(";");
            int expected = Integer.parseInt(fields[0]);
            int[] heights = Arrays.stream(fields[1].split(",")).mapToInt(Integer::parseInt).toArray();
            byte[] cells = new byte[27 * 128];

            for (int x = 0; x < 27; x++) {
                if (heights[x] >= 0) {
                    for (int y = heights[x]; y < 128; y++) {
                        cells[y * 27 + x] = 1;
                    }
                }
            }

            var layer = new TerrainLayerDefinition(0, 0, 27, 128, cells);
            assertEquals(expected, MatchTerrainSupport.settledY(List.of(layer), 13, 0, 128), line);
        }
    }

    @Test
    void keepsUnsupportedEndpointsAndCombinesOverlappingLayersWhileSkippingRemovedGround() {
        var empty = new TerrainLayerDefinition(0, 0, 1, 1, new byte[]{0});
        var removed = new TerrainLayerDefinition(0, 10, 1, 1, new byte[]{17});
        var solid = new TerrainLayerDefinition(0, 20, 1, 1, new byte[]{2});
        var further = new TerrainLayerDefinition(0, 30, 1, 1, new byte[]{1});

        assertEquals(129, MatchTerrainSupport.settledY(List.of(solid), 0, 129, 128));
        assertEquals(0, MatchTerrainSupport.settledY(List.of(empty), 0, 0, 128));
        assertEquals(
            0,
            MatchTerrainSupport.settledY(List.of(new TerrainLayerDefinition(0, 0, 1, 1, new byte[]{1})), 0, 0, 128)
        );
        assertEquals(0, MatchTerrainSupport.settledY(List.of(solid), -1, 0, 128));
        assertEquals(0, MatchTerrainSupport.settledY(List.of(solid), 1, 0, 128));
        assertEquals(25, MatchTerrainSupport.settledY(List.of(solid), 0, 25, 128));
        assertEquals(
            0,
            MatchTerrainSupport.settledY(List.of(new TerrainLayerDefinition(0, 200, 1, 1, new byte[]{1})), 0, 0, 128)
        );
        assertEquals(20, MatchTerrainSupport.settledY(List.of(empty, removed, further, solid), 0, 0, 128));
    }
}
