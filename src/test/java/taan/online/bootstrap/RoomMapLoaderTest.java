package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;

class RoomMapLoaderTest {
    @Test
    void derivesAvailabilityFromTheSingleMapCatalogAndChoosesAnAllowedInitialMap() {
        var loader = new RoomDefinitionLoader();
        var maps = loader.maps(MatchFixtures.catalog());

        assertEquals(21, maps.mapIds().size());
        assertEquals(Set.of(1, 2, 4, 9, 10, 17), maps.forKind(0).mapIds());
        assertEquals(19, maps.forKind(1).mapIds().size());
        assertEquals(
            Set.of(3, 5, 6, 7, 8, 11, 12, 13, 14, 15, 18, 19, 20, 21),
            maps.forKind(4).mapIds()
        );
        assertEquals(maps.mapIds(), maps.forKind(6).mapIds());
        assertEquals(1, maps.initialMap(0, 1));
        assertEquals(3, maps.initialMap(4, 1));
        assertEquals(13, maps.initialMap(4, 13));
        assertThrows(
            IllegalArgumentException.class,
            () -> loader.defaults(
                new ByteArrayInputStream("map.id=200\nweapon.id=0".getBytes(StandardCharsets.UTF_8)),
                maps
            )
        );
    }
}
