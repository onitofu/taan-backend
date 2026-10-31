package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import taan.online.MatchFixtures;
import taan.online.match.model.WeaponAttackKey;
import taan.online.match.model.WeaponMissileKey;

class WeaponFireLoaderTest {
    @ParameterizedTest
    @CsvSource({
        "1,0,6,0,0",
        "1,1,7,1,1",
        "1,2,8,2:3:4:5:6:7:8,2:2:2:2:2:2:2",
        "2,0,9,0,0",
        "2,1,10,1,1",
        "2,2,11,2:3:4:5,2:2:2:2",
        "6,0,6,0:1:2,2:3:4",
        "6,1,7,3:4:5:6,1:2:3:4",
        "6,2,8,7:8:9:10:11,0:1:2:3:4"
    })
    void keepsResourceOrderAndMissileTypesDistinctFromTimelineOrder(
        int weapon,
        int kind,
        int animation,
        String variants,
        String missiles
    ) {
        var attack = MatchFixtures.attacks().get(new WeaponAttackKey(weapon, kind));
        assertEquals(animation, attack.animationType());
        assertEquals(weapon == 2 ? 39 : 38, attack.specialAnimation());
        assertFalse(attack.intrinsicGuidance());
        var expectedVariants = variants.split(":");
        var expectedTypes = missiles.split(":");
        assertEquals(expectedVariants.length, attack.fires().size());

        for (int index = 0; index < expectedVariants.length; index++) {
            assertEquals(Integer.parseInt(expectedVariants[index]), attack.fires().get(index).missileVariant());
            assertEquals(Integer.parseInt(expectedTypes[index]), attack.fires().get(index).missileTypeId());
        }
    }

    @Test
    void checksTheKnownExplosionMaskAnchors() throws Exception {
        var definitions = MatchFixtures.definitions();
        int[][] centers = {{138, 139, 136}, {144, 146, 140}, {139, 138, 137, 135, 133}};
        int[] weapons = {1, 2, 6};

        for (int index = 0; index < weapons.length; index++) {
            for (int type = 0; type < centers[index].length; type++) {
                var mask = definitions.missiles().get(new WeaponMissileKey(weapons[index], type));
                assertEquals(128, mask.centerX());
                assertEquals(centers[index][type], mask.centerY());
            }
        }
    }

    @Test
    void rejectsMissingOrphanReorderedAndMalformedFireResourcesBeforeServingMatches() throws Exception {
        String fires = text("weapon-fires.csv");

        for (String invalid : List.of(
            fires.lines().findFirst().orElseThrow() + "\n",
            fires.replace("fire_index", "wrong"),
            fires.replace("1,2,1,3,2", "1,2,3,3,2"),
            fires.replace("1,2,1,3,2", "1,2,1,2,2"),
            fires.replace("1,2,6,8,2\n", ""),
            fires.replace("1,2,6,8,2", "1,2,6,8,65535"),
            fires.replace("0,0,0,0,0\n", ""),
            fires + "4,0,0,0,0\n",
            fires + "0,0,0,0,0\n",
            fires.replace("1,2,1,3,2", "1,2"),
            fires.replace("1,2,1,3,2", "1,2,x,3,2")
        )) {
            assertThrows(IllegalArgumentException.class, () -> load(input(invalid)));
        }

        assertThrows(NullPointerException.class, () -> load(null));

        for (boolean failRead : new boolean[]{true, false}) {
            var failing = new BrokenDefinitionStream(fires.getBytes(StandardCharsets.UTF_8), failRead);
            assertThrows(IllegalStateException.class, () -> load(failing));
        }

        var tracking = new TrackingDefinitionStream(fires.getBytes(StandardCharsets.UTF_8));
        load(tracking);
        assertTrue(tracking.closed());
    }

    private void load(InputStream fires) {
        new MatchDefinitionLoader().load(
            resource("maps.csv"),
            resource("map-spawns.csv"),
            resource("weapons.csv"),
            resource("weapon-attacks.csv"),
            name -> name.equals("weapon-fires.csv") ? fires : resource(name)
        );
    }

    private String text(String name) throws Exception {
        try (var stream = resource(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/match/" + name);
    }
}
