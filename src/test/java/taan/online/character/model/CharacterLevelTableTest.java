package taan.online.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.TestLevels;

class CharacterLevelTableTest {
    @Test
    void usesEveryFrozenThresholdAndKeepsMaximumLevelDisplayNondegenerate() {
        var table = TestLevels.table();

        for (int level = 1; level <= 109; level++) {
            assertEquals(level, table.level(table.threshold(level)));

            if (level > 1) {
                assertEquals(level - 1, table.level(table.threshold(level) - 1));
            }
        }

        assertEquals(940, table.threshold(2));
        assertEquals(25230, table.threshold(10));
        assertEquals(547160, table.threshold(71));
        assertEquals(1048576, table.threshold(109));
        assertEquals(1034050, table.displayStart(109));
        assertEquals(1048576, table.displayNext(109));
        assertEquals(0, table.displayStart(1));
        assertEquals(940, table.displayNext(1));
        assertThrows(IllegalArgumentException.class, () -> table.level(-1));
        assertThrows(IllegalArgumentException.class, () -> table.level(1048577));
        assertThrows(IllegalArgumentException.class, () -> table.threshold(0));
        assertThrows(IllegalArgumentException.class, () -> table.threshold(110));
    }

    @Test
    void rejectsMissingUnorderedAndInvalidDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterLevelTable(List.of()));

        for (int index : new int[]{0, 1, 108}) {
            var definitions = definitions();
            definitions.set(index, new LevelDefinition(index + 1, index == 0 ? 1 : 0));
            assertThrows(IllegalArgumentException.class, () -> new CharacterLevelTable(definitions));
        }

        var unordered = definitions();
        unordered.set(1, new LevelDefinition(3, 940));
        assertThrows(IllegalArgumentException.class, () -> new CharacterLevelTable(unordered));
        assertThrows(IllegalArgumentException.class, () -> new LevelDefinition(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelDefinition(110, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelDefinition(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new LevelDefinition(1, 1048577));
        var originals = definitions();
        var table = new CharacterLevelTable(originals);
        originals.clear();

        assertEquals(109, table.level(1048576));
    }

    private static ArrayList<LevelDefinition> definitions() {
        var definitions = new ArrayList<LevelDefinition>();

        for (int level = 1; level <= 109; level++) {
            definitions.add(new LevelDefinition(level, TestLevels.table().threshold(level)));
        }

        return definitions;
    }
}
