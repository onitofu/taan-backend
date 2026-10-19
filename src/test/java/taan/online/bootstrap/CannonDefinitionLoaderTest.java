package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.model.CannonShotDefinition;

class CannonDefinitionLoaderTest {
    @Test
    void loadsTheVersionedOrdinaryCannonPermissionsAndChecksTheirExportHash() throws Exception {
        var catalog = MatchFixtures.catalog();
        byte[] bytes;
        String provenance;

        try (var stream = resource("cannon-shots.csv"); var evidence = resource("catalog-provenance.json")) {
            bytes = stream.readAllBytes();
            provenance = new String(evidence.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(new CannonShotDefinition(0, 0, 0, 100, 0, 0), catalog.cannonShot());
        assertEquals(catalog.cannonShot(), catalog.select(1).cannonShot());
        assertTrue(provenance.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
        String csv = new String(bytes, StandardCharsets.UTF_8);

        for (String invalid : new String[]{
            csv.lines().findFirst().orElseThrow() + "\n",
            csv + "0,0,0,100,0,0\n",
            csv.replace("weapon_id", "wrong"),
            csv.replace("0,0,0,100,0,0", "0,0"),
            csv.replace("0,0,0,100,0,0", "x,0,0,100,0,0")
        }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> load(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8)))
            );
        }

        assertThrows(NullPointerException.class, () -> load(null));

        for (boolean failRead : new boolean[]{true, false}) {
            assertThrows(IllegalStateException.class, () -> load(new BrokenDefinitionStream(bytes, failRead)));
        }
    }

    @Test
    void rejectsUnsupportedIdentitiesAndInvalidPowerRangesWithoutImportingCriticalPlaceholders() {
        int[][] invalid = {
            {1, 0, 0, 100, 0, 0},
            {0, 1, 0, 100, 0, 0},
            {0, 0, -1, 100, 0, 0},
            {0, 0, 0, 101, 0, 0},
            {0, 0, 50, 49, 0, 0},
            {0, 0, 0, 100, 1, 0},
            {0, 0, 0, 100, 0, 1}
        };

        for (var row : invalid) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new CannonShotDefinition(row[0], row[1], row[2], row[3], row[4], row[5])
            );
        }
    }

    private void load(InputStream cannon) {
        new MatchDefinitionLoader().load(
            resource("catalog.properties"),
            resource("maps.csv"),
            resource("map-spawns.csv"),
            resource("weapons.csv"),
            cannon
        );
    }

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/match/" + name);
    }
}
