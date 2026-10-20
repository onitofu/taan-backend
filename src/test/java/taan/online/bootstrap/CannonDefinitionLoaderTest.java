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
import taan.online.match.model.WeaponAttackDefinition;
import taan.online.match.model.WeaponAttackKey;

class CannonDefinitionLoaderTest {
    @Test
    void loadsTheVersionedOrdinaryCannonPermissionsAndChecksTheirExportHash() throws Exception {
        var catalog = MatchFixtures.catalog();
        byte[] bytes;
        String provenance;

        try (var stream = resource("weapon-attacks.csv"); var evidence = resource("catalog-provenance.json")) {
            bytes = stream.readAllBytes();
            provenance = new String(evidence.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(
            new WeaponAttackDefinition(new WeaponAttackKey(0, 0), 0, 100, 0, 0, 9, false),
            catalog.attacks().get(new WeaponAttackKey(0, 0))
        );
        assertEquals(
            catalog.attacks().get(new WeaponAttackKey(0, 0)),
            catalog.select(1).attacks().get(new WeaponAttackKey(0, 0))
        );
        assertEquals(
            new WeaponAttackDefinition(new WeaponAttackKey(0, 1), 0, 100, 1, 1, 10, false),
            catalog.attacks().get(new WeaponAttackKey(0, 1))
        );
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
            {-1, 0, 0, 100, 0, 0},
            {12, 0, 0, 100, 0, 0},
            {0, -1, 0, 100, 0, 0},
            {0, 3, 0, 100, 3, 3},
            {0, 0, -1, 100, 0, 0},
            {0, 0, 0, 101, 0, 0},
            {0, 0, 50, 49, 0, 0},
            {0, 0, 0, 100, -1, 0},
            {0, 0, 0, 100, 256, 0},
            {0, 0, 0, 100, 0, -1},
            {0, 0, 0, 100, 0, 65536}
        };

        for (var row : invalid) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new WeaponAttackDefinition(
                    new WeaponAttackKey(row[0], row[1]),
                    row[2],
                    row[3],
                    row[4],
                    row[5],
                    9 + row[1],
                    false
                )
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
