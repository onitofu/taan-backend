package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import taan.online.match.model.MatchDefinitions;

class MatchDefinitionLoaderTest {
    @Test
    void loadsAllFourteenRawAndSettledSpawnsAndTwelveReferenceWeapons() {
        var definitions = load(resource("jungle.properties"), resource("jungle-spawns.csv"), resource("weapons.csv"));

        assertEquals(
            "d7d54a39d0e5d75819894c812acd34b39e803fa1f0e7fe87d519f4a211e4b67f",
            definitions.catalogVersion()
        );
        assertEquals(1, definitions.map().mapId());
        assertEquals("jungle", definitions.map().asset());
        assertEquals(1300, definitions.map().width());
        assertEquals(950, definitions.map().height());
        assertEquals(14, definitions.map().spawns().size());
        assertEquals(12, definitions.weapons().size());

        int[][] expected = {
            {97, 686, 97, 685, 1, 0},
            {336, 677, 336, 676, 1, 0},
            {176, 692, 176, 691, 1, 0},
            {1110, 686, 1110, 685, 2, 0},
            {948, 663, 948, 662, 2, 0},
            {847, 658, 847, 658, 0, 1},
            {674, 675, 674, 675, 0, 1},
            {589, 670, 589, 669, 0, 1},
            {427, 662, 427, 662, 0, 1},
            {258, 681, 258, 681, 1, 2},
            {516, 648, 516, 647, 1, 2},
            {1034, 685, 1034, 684, 2, 2},
            {747, 684, 747, 682, 2, 2},
            {1196, 669, 1196, 669, 2, 0}
        };

        for (int index = 0; index < expected.length; index++) {
            var spawn = definitions.map().spawns().get(index);
            int[] row = expected[index];

            assertEquals(index, spawn.index());
            assertEquals(row[0], spawn.rawX());
            assertEquals(row[1], spawn.rawY());
            assertEquals(row[2], spawn.settledX());
            assertEquals(row[3], spawn.settledY());
            assertEquals(row[4], spawn.side());
            assertEquals(row[5], spawn.flag());
            assertTrue(spawn.valid());
        }

        int[] moves = {2500, 3000, 3000, 4000, 5000, 4500, 3000, 4000, 2500, 3500, 6000, 4500};
        String[] assets = {
            "cannon",
            "dynamite",
            "gun",
            "crossbow",
            "paper",
            "hammer",
            "fan",
            "flyingdagger",
            "bee",
            "fish",
            "thunder",
            "budu"
        };

        for (int index = 0; index < 12; index++) {
            var weapon = definitions.weapons().get(index);

            assertEquals(index, weapon.weaponId());
            assertEquals(assets[index], weapon.asset());
            assertEquals(1000, weapon.resourceMaxHp());
            assertEquals(100, weapon.maxEnergy());
            assertEquals(moves[index], weapon.resourceMove());
        }
    }

    @Test
    void rejectsMalformedPropertiesCsvNumbersAndValidityWithoutFallingBackToDefaults() throws Exception {
        String properties = text("jungle.properties");
        String spawns = text("jungle-spawns.csv");
        String weapons = text("weapons.csv");

        for (String invalid : new String[]{
            properties.replace("map.id=1", "map.id=no"),
            properties + "unknown=1\n",
            properties.replace("map.id=1\n", "")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(input(invalid), input(spawns), input(weapons)));
        }

        for (String invalid : new String[]{
            spawns.replace("index,raw_x", "wrong,raw_x"),
            spawns.replace("0,97,686", "0,97"),
            spawns.replace("true", "maybe"),
            spawns.replace("0,97,686", "x,97,686")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(input(properties), input(invalid), input(weapons)));
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> load(input(properties), input(spawns), input(weapons.replace("weapon_id", "wrong")))
        );
        assertFalse(
            load(input(properties), input(spawns.replace("true", "false")), input(weapons))
                .map().spawns().getFirst().valid()
        );
    }

    @Test
    void propagatesMalformedUtf8ReadAndCloseFailuresWithoutLoggingRawContents() throws Exception {
        byte[] properties = resource("jungle.properties").readAllBytes();
        byte[] spawns = resource("jungle-spawns.csv").readAllBytes();
        byte[] weapons = resource("weapons.csv").readAllBytes();

        for (boolean failRead : new boolean[]{true, false}) {
            assertThrows(
                IllegalStateException.class,
                () -> load(new BrokenDefinitionStream(properties, failRead), input(spawns), input(weapons))
            );
            assertThrows(
                IllegalStateException.class,
                () -> load(input(properties), new BrokenDefinitionStream(spawns, failRead), input(weapons))
            );
            assertThrows(
                IllegalStateException.class,
                () -> load(input(properties), input(spawns), new BrokenDefinitionStream(weapons, failRead))
            );
        }

        assertThrows(
            IllegalStateException.class,
            () -> load(input(new byte[]{(byte) 0xff}), input(spawns), input(weapons))
        );
        assertThrows(NullPointerException.class, () -> load(null, input(spawns), input(weapons)));
    }

    @Test
    void closesAllProvidedResourcesEvenWhenTheFirstDefinitionIsInvalid() throws Exception {
        var properties = new TrackingDefinitionStream(new byte[0]);
        var spawns = new TrackingDefinitionStream(resource("jungle-spawns.csv").readAllBytes());
        var weapons = new TrackingDefinitionStream(resource("weapons.csv").readAllBytes());

        assertThrows(IllegalArgumentException.class, () -> load(properties, spawns, weapons));
        assertTrue(properties.closed());
        assertTrue(spawns.closed());
        assertTrue(weapons.closed());
    }

    private MatchDefinitions load(InputStream properties, InputStream spawns, InputStream weapons) {
        return new MatchDefinitionLoader().load(properties, spawns, weapons);
    }

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/match/" + name);
    }

    private String text(String name) throws Exception {
        try (var input = resource(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ByteArrayInputStream input(String value) {
        return input(value.getBytes(StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream input(byte[] value) {
        return new ByteArrayInputStream(value);
    }
}
