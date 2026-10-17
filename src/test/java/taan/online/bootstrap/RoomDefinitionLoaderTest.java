package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import taan.online.room.model.RoomBootstrapDefinition;
import taan.online.room.model.RoomTitle;

class RoomDefinitionLoaderTest {
    @Test
    void loadsVersionedDefaultsAndReusesTheExistingFilterWithoutDuplicatingIt() {
        var loader = new RoomDefinitionLoader();

        assertEquals(
            new RoomBootstrapDefinition(1, 3),
            loader.defaults(getClass().getResourceAsStream("/room/bootstrap.properties"))
        );
        assertTrue(
            loader.titleFilter(getClass().getResourceAsStream("/character/name-filters.txt"))
                .forbids(new RoomTitle("fuck"))
        );
        assertThrows(IllegalArgumentException.class, () -> loader.defaults(input("map.id=1")));
        assertThrows(IllegalArgumentException.class, () -> loader.defaults(input("map.id=x\nweapon.id=3")));
        assertThrows(IllegalArgumentException.class, () -> loader.titleFilter(input("word\n")));
        assertThrows(NullPointerException.class, () -> loader.defaults(null));
        assertThrows(NullPointerException.class, () -> loader.titleFilter(null));
    }

    @Test
    void propagatesReadAndCloseFailuresWithoutSilentlyUsingDefaults() throws Exception {
        var loader = new RoomDefinitionLoader();
        byte[] defaults = "map.id=1\nweapon.id=3".getBytes(StandardCharsets.UTF_8);
        byte[] filters = getClass().getResourceAsStream("/character/name-filters.txt").readAllBytes();

        assertThrows(IllegalStateException.class, () -> loader.defaults(new BrokenDefinitionStream(defaults, false)));
        assertThrows(IllegalStateException.class, () -> loader.defaults(new BrokenDefinitionStream(new byte[0], true)));
        assertThrows(IllegalStateException.class, () -> loader.titleFilter(new BrokenDefinitionStream(filters, false)));
        assertThrows(
            IllegalStateException.class,
            () -> loader.titleFilter(new BrokenDefinitionStream(new byte[0], true))
        );
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
