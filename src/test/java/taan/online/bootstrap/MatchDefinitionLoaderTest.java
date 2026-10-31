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
    void loadsRawAndSettledSpawnsAndOnlyImplementedWeapons() {
        var definitions = load(resource("map-spawns.csv"), resource("weapons.csv"));

        assertEquals(1, definitions.map().mapId());
        assertEquals(1300, definitions.map().width());
        assertEquals(950, definitions.map().height());
        assertEquals(14, definitions.map().spawns().size());
        assertEquals(8, definitions.weapons().size());

        int[][] expected = {
            {97, 686, 97, 685, 1},
            {336, 677, 336, 676, 1},
            {176, 692, 176, 691, 1},
            {1110, 686, 1110, 685, 2},
            {948, 663, 948, 662, 2},
            {847, 658, 847, 658, 0},
            {674, 675, 674, 675, 0},
            {589, 670, 589, 669, 0},
            {427, 662, 427, 662, 0},
            {258, 681, 258, 681, 1},
            {516, 648, 516, 647, 1},
            {1034, 685, 1034, 684, 2},
            {747, 684, 747, 682, 2},
            {1196, 669, 1196, 669, 2}
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
            assertTrue(spawn.valid());
        }

        int[] ids = {0, 1, 2, 3, 6, 8, 10, 100};
        int[] moves = {2500, 3000, 3000, 4000, 3000, 2500, 6000, 5000};

        for (int index = 0; index < ids.length; index++) {
            var weapon = definitions.weapons().get(ids[index]);

            assertEquals(ids[index], weapon.weaponId());
            assertEquals(1000, weapon.resourceMaxHp());
            assertEquals(100, weapon.maxEnergy());
            assertEquals(moves[index], weapon.resourceMove());
        }
    }

    @Test
    void rejectsMalformedCsvNumbersAndValidityWithoutFallingBackToDefaults() throws Exception {
        String spawns = text("map-spawns.csv");
        String weapons = text("weapons.csv");

        for (String invalid : new String[]{
            spawns.replace("index,raw_x", "wrong,raw_x"),
            spawns.replace("0,97,686", "0,97"),
            spawns.replace("true", "maybe"),
            spawns.replace("0,97,686", "x,97,686")
        }) {
            assertThrows(IllegalArgumentException.class, () -> load(input(invalid), input(weapons)));
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> load(input(spawns), input(weapons.replace("weapon_id", "wrong")))
        );
        assertFalse(
            load(input(spawns.replace("true", "false")), input(weapons))
                .map().spawns().getFirst().valid()
        );
    }

    @Test
    void propagatesMalformedUtf8ReadAndCloseFailuresWithoutLoggingRawContents() throws Exception {
        byte[] spawns = resource("map-spawns.csv").readAllBytes();
        byte[] weapons = resource("weapons.csv").readAllBytes();

        for (boolean failRead : new boolean[]{true, false}) {
            assertThrows(
                IllegalStateException.class,
                () -> load(new BrokenDefinitionStream(spawns, failRead), input(weapons))
            );
            assertThrows(
                IllegalStateException.class,
                () -> load(input(spawns), new BrokenDefinitionStream(weapons, failRead))
            );
        }

        assertThrows(
            IllegalStateException.class,
            () -> load(input(new byte[]{(byte) 0xff}), input(weapons))
        );
        assertThrows(NullPointerException.class, () -> load(null, input(weapons)));
    }

    @Test
    void closesAllProvidedResourcesEvenWhenTheFirstDefinitionIsInvalid() throws Exception {
        var maps = new TrackingDefinitionStream(new byte[0]);
        var spawns = new TrackingDefinitionStream(resource("map-spawns.csv").readAllBytes());
        var weapons = new TrackingDefinitionStream(resource("weapons.csv").readAllBytes());

        var attacks = new TrackingDefinitionStream(resource("weapon-attacks.csv").readAllBytes());

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitionLoader().load(maps, spawns, weapons, attacks)
        );
        assertTrue(maps.closed());
        assertTrue(attacks.closed());
        assertTrue(spawns.closed());
        assertTrue(weapons.closed());
    }

    @Test
    void rejectsDuplicateMissingAndOrphanMapDataAndClosesFailingMapStreams() throws Exception {
        var loader = new MatchDefinitionLoader();
        String maps = text("maps.csv");
        String spawns = text("map-spawns.csv");
        String weapons = text("weapons.csv");
        String firstMap = maps.lines().skip(1).findFirst().orElseThrow();

        for (String invalid : new String[]{
            maps + firstMap + "\n",
            maps.replace(firstMap + "\n", ""),
            maps.replace("map_id,width", "wrong,width"),
            maps.replace(",true,true,false,true", ",true,maybe,false,true")
        }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(
                    input(invalid),
                    input(spawns),
                    input(weapons),
                    MatchDefinitionLoaderTest.class.getResourceAsStream("/match/weapon-attacks.csv")
                )
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> loader.load(
                input(maps),
                input(spawns + "22,0,1,1,1,1,0,true\n"),
                input(weapons),
                MatchDefinitionLoaderTest.class.getResourceAsStream("/match/weapon-attacks.csv")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> loader.load(
                input(maps),
                input(spawns.replace("1,0,97,686", "22,0,97,686")),
                input(weapons),
                MatchDefinitionLoaderTest.class.getResourceAsStream("/match/weapon-attacks.csv")
            )
        );

        for (boolean failRead : new boolean[]{true, false}) {
            assertThrows(
                IllegalStateException.class,
                () -> loader.load(
                    new BrokenDefinitionStream(maps.getBytes(StandardCharsets.UTF_8), failRead),
                    input(spawns),
                    input(weapons),
                    MatchDefinitionLoaderTest.class.getResourceAsStream("/match/weapon-attacks.csv")
                )
            );
        }
    }

    private MatchDefinitions load(InputStream spawns, InputStream weapons) {
        return new MatchDefinitionLoader().load(
            resource("maps.csv"),
            spawns,
            weapons,
            MatchDefinitionLoaderTest.class.getResourceAsStream("/match/weapon-attacks.csv")
        ).select(1);
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
