package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CharacterLevelLoaderTest {
    @Test
    void loadsTheVersionedTableAndRejectsMissingMalformedAndUnreadableSources() {
        var loader = new CharacterLevelLoader();
        assertEquals(109, loader.load(getClass().getResourceAsStream("/character/levels.csv")).level(1048576));
        assertThrows(NullPointerException.class, () -> loader.load(null));

        for (String invalid : new String[]{
            "", "wrong", "level,total_exp\ninvalid\n",
            "level,total_exp\ninvalid,0\n"
        }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(
                    new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))
                )
            );
        }

        assertThrows(IllegalArgumentException.class, () -> loader.load(new BrokenDefinitionStream(new byte[0], true)));
        assertThrows(IllegalArgumentException.class, () -> loader.load(new BrokenDefinitionStream(new byte[0], false)));
    }
}
