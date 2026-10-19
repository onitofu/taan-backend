package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class MatchDefinitionsTest {
    @Test
    void rejectsInvalidSpawnValuesAndSeparatesRawFromSettledCoordinates() {
        int[][] invalid = {
            {-1, 0, 0, 0, 0, 0, 0},
            {0, -1, 0, 0, 0, 0, 0},
            {0, 0, -1, 0, 0, 0, 0},
            {0, 0, 0, -1, 0, 0, 0},
            {0, 0, 0, 0, -1, 0, 0},
            {0, 0, 0, 0, 0, -1, 0},
            {0, 0, 0, 0, 0, 3, 0},
            {0, 0, 0, 0, 0, 0, -1},
            {0, 0, 0, 0, 0, 0, 256}
        };

        for (int[] row : invalid) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MapSpawnDefinition(row[0], row[1], row[2], row[3], row[4], row[5], row[6], true)
            );
        }

        var spawn = new MapSpawnDefinition(13, 747, 684, 747, 682, 2, 255, false);

        assertEquals(684, spawn.rawY());
        assertEquals(682, spawn.settledY());
        assertEquals(false, spawn.valid());
    }

    @Test
    void rejectsInvalidMapBoundsIdentityAndDuplicateSpawnIndices() {
        var spawns = spawns();

        for (int[] values : List.of(
            new int[]{0, 1300, 950},
            new int[]{65536, 1300, 950},
            new int[]{1, 0, 950},
            new int[]{1, 1300, 0}
        )) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchMapDefinition(
                    values[0],
                    "jungle",
                    values[1],
                    values[2],
                    spawns,
                    Set.of(0, 1, 6),
                    MatchFixtures.terrainLayers()
                )
            );
        }

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchMapDefinition(1, " ", 1300, 950, spawns, Set.of(0, 1, 6), MatchFixtures.terrainLayers())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchMapDefinition(
                1,
                "jungle",
                1300,
                950,
                List.of(),
                Set.of(0, 1, 6),
                MatchFixtures.terrainLayers()
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchMapDefinition(
                1,
                "jungle",
                1300,
                950,
                List.of(spawns.getFirst(), spawns.getFirst()),
                Set.of(0, 1, 6),
                MatchFixtures.terrainLayers()
            )
        );

        for (var spawn : List.of(
            new MapSpawnDefinition(0, 1300, 0, 0, 0, 0, 0, true),
            new MapSpawnDefinition(0, 0, 950, 0, 0, 0, 0, true),
            new MapSpawnDefinition(0, 0, 0, 1300, 0, 0, 0, true),
            new MapSpawnDefinition(0, 0, 0, 0, 950, 0, 0, true)
        )) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new MatchMapDefinition(
                    1,
                    "jungle",
                    1300,
                    950,
                    List.of(spawn),
                    Set.of(0, 1, 6),
                    MatchFixtures.terrainLayers()
                )
            );
        }
    }

    @Test
    void validatesWeaponResourceValuesWithoutChoosingInitialCombatStatistics() {
        for (int[] values : List.of(
            new int[]{-1, 1000, 100, 2500},
            new int[]{12, 1000, 100, 2500},
            new int[]{0, 0, 100, 2500},
            new int[]{0, 1000, 0, 2500},
            new int[]{0, 1000, 65536, 2500},
            new int[]{0, 1000, 100, 0}
        )) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new WeaponDefinition(values[0], "cannon", values[1], values[2], values[3])
            );
        }

        assertThrows(IllegalArgumentException.class, () -> new WeaponDefinition(0, "", 1000, 100, 2500));
        assertEquals(65535, new WeaponDefinition(11, "budu", 1000, 65535, 4500).maxEnergy());
    }

    @Test
    void validatesVersionsAndOrderingWithoutAssumingOneMapOrSpawnCount() {
        var spawns = new ArrayList<>(spawns());
        var weapons = new ArrayList<>(weapons());
        var map = new MatchMapDefinition(
            1,
            "jungle",
            1300,
            950,
            spawns,
            Set.of(0, 1, 6),
            MatchFixtures.terrainLayers()
        );
        var definitions = new MatchDefinitions(
            "a".repeat(64),
            map,
            weapons,
            MatchFixtures.cannonShots(),
            MatchFixtures.missileTerrain()
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions("bad", map, weapons, MatchFixtures.cannonShots(), MatchFixtures.missileTerrain())
        );
        assertEquals(
            2,
            new MatchDefinitions(
                "a".repeat(64),
                new MatchMapDefinition(
                    2,
                    "other",
                    1300,
                    950,
                    spawns.subList(0, 13),
                    Set.of(6),
                    MatchFixtures.terrainLayers()
                ),
                weapons,
                MatchFixtures.cannonShots(),
                MatchFixtures.missileTerrain()
            ).map().mapId()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(
                "a".repeat(64),
                map,
                weapons.subList(0, 11),
                MatchFixtures.cannonShots(),
                MatchFixtures.missileTerrain()
            )
        );

        var reordered = new ArrayList<>(spawns);
        var first = reordered.set(0, reordered.get(1));
        reordered.set(1, first);

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(
                "a".repeat(64),
                new MatchMapDefinition(
                    1,
                    "jungle",
                    1300,
                    950,
                    reordered,
                    Set.of(0, 1, 6),
                    MatchFixtures.terrainLayers()
                ),
                weapons,
                MatchFixtures.cannonShots(),
                MatchFixtures.missileTerrain()
            )
        );

        var swapped = new ArrayList<>(weapons);
        var weapon = swapped.set(0, swapped.get(1));
        swapped.set(1, weapon);

        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchDefinitions(
                "a".repeat(64),
                map,
                swapped,
                MatchFixtures.cannonShots(),
                MatchFixtures.missileTerrain()
            )
        );
        spawns.clear();
        weapons.clear();

        assertEquals(14, definitions.map().spawns().size());
        assertEquals(12, definitions.weapons().size());
        assertThrows(UnsupportedOperationException.class, () -> definitions.map().spawns().clear());
        assertThrows(UnsupportedOperationException.class, () -> definitions.weapons().clear());
    }

    private List<MapSpawnDefinition> spawns() {
        return IntStream.range(0, 14)
            .mapToObj(index -> new MapSpawnDefinition(index, 97, 686, 97, 685, 1, 0, true)).toList();
    }

    private List<WeaponDefinition> weapons() {
        return IntStream.range(0, 12)
            .mapToObj(index -> new WeaponDefinition(index, "weapon", 1000, 100, 2500)).toList();
    }
}
