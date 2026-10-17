package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.character.model.CharacterName;

class CharacterDefinitionLoaderTest {
    private static final String HEADER = "gender,slot,item_id,palette_1_id,palette_2_id,palette_3_id\n";

    @Test
    void loadsTheExactSourceListsAndCreationCategories() throws Exception {
        byte[] filters = resource("name-filters.txt");
        var lines = new String(filters, StandardCharsets.UTF_8).lines().toList();

        assertEquals(2962, lines.size());
        assertEquals(2, lines.stream().filter(value -> value.endsWith("\u00a0")).count());
        assertEquals(37, lines.stream().filter(value -> value.contains("*")).count());
        assertTrue(new CharacterDefinitionLoader().nameFilter(input(filters)).forbids(new CharacterName("ADMIN")));
        assertEquals(37, new String(resource("creation-options.csv"), StandardCharsets.UTF_8).lines().count());
    }

    @Test
    void rejectsMissingTruncatedMalformedAndDuplicateDefinitions() throws Exception {
        var loader = new CharacterDefinitionLoader();

        assertThrows(NullPointerException.class, () -> loader.nameFilter(null));
        assertThrows(NullPointerException.class, () -> loader.creationOptions(null));
        assertThrows(IllegalArgumentException.class, () -> loader.nameFilter(input(new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> loader.nameFilter(input("\n".repeat(2962))));

        for (String value : new String[]{
            "",
            HEADER + "1,1\n",
            HEADER + "3,1,729,1,1,1\n",
            HEADER + "1,1,729,1,1,1\n1,1,729,1,1,1\n",
            HEADER + "a,1,729,1,1,1\n"
        }) {
            assertThrows(IllegalArgumentException.class, () -> loader.creationOptions(input(value)));
        }

        assertThrows(
            IllegalStateException.class,
            () -> loader.nameFilter(new BrokenDefinitionStream(resource("name-filters.txt"), false))
        );
        assertThrows(
            IllegalStateException.class,
            () -> loader.nameFilter(new BrokenDefinitionStream(new byte[0], true))
        );
        assertThrows(IllegalStateException.class, () -> loader.nameFilter(input(new byte[]{(byte) 0xff})));
        assertThrows(
            IllegalStateException.class,
            () -> loader.creationOptions(new BrokenDefinitionStream(resource("creation-options.csv"), false))
        );
        assertThrows(
            IllegalStateException.class,
            () -> loader.creationOptions(new BrokenDefinitionStream(new byte[0], true))
        );
    }

    private static byte[] resource(String name) throws Exception {
        try (var input = CharacterFixtures.class.getResourceAsStream("/character/" + name)) {
            return input.readAllBytes();
        }
    }

    private static ByteArrayInputStream input(String value) {
        return input(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteArrayInputStream input(byte[] value) {
        return new ByteArrayInputStream(value);
    }
}
