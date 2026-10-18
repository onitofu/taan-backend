package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;

class RoomMapLoaderTest {
    @Test
    void loadsExactlyTheApprovedOrdinaryMapsAndRejectsUnavailableBootstrapMaps() {
        var loader = new RoomDefinitionLoader();

        assertEquals(RoomScenario.maps(), loader.maps(getClass().getResourceAsStream("/room/maps.csv")));
        assertThrows(
            IllegalArgumentException.class,
            () -> loader.defaults(input("map.id=200\nweapon.id=0"), RoomScenario.maps())
        );

        for (String text : new String[]{
            "",
            "id\n1",
            "map_id\n",
            "map_id\n1\n1",
            "map_id\n0",
            "map_id\n65536",
            "map_id\nx"
        }) {
            assertThrows(IllegalArgumentException.class, () -> loader.maps(input(text)));
        }

        assertThrows(NullPointerException.class, () -> loader.maps(null));
    }

    @Test
    void propagatesReadCloseAndInvalidUtf8Failures() {
        var loader = new RoomDefinitionLoader();
        byte[] valid = "map_id\n1\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> loader.maps(new BrokenDefinitionStream(valid, false)));
        assertThrows(IllegalStateException.class, () -> loader.maps(new BrokenDefinitionStream(new byte[0], true)));
        assertThrows(IllegalStateException.class, () -> loader.maps(new ByteArrayInputStream(new byte[]{(byte) 0xff})));
    }

    private ByteArrayInputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
