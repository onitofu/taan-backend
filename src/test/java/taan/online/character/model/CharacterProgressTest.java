package taan.online.character.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CharacterProgressTest {
    @Test
    void startsAtZeroAndPreservesEachUnsignedCounterWithoutWrapping() {
        assertEquals(new CharacterProgress(0, 0, 0, 0), CharacterProgress.initial());
        var progress = new CharacterProgress(1048576, 12, 23, 34);

        assertEquals(1048576, progress.experience());
        assertThrows(IllegalArgumentException.class, () -> new CharacterProgress(1048577, 0, 0, 0));
        assertEquals(12, progress.forceMedals());
        assertEquals(23, progress.killMedals());
        assertEquals(34, progress.supplyMedals());

        for (long invalid : new long[]{-1, 0x100000000L}) {
            assertThrows(IllegalArgumentException.class, () -> new CharacterProgress(invalid, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CharacterProgress(0, invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CharacterProgress(0, 0, invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> new CharacterProgress(0, 0, 0, invalid));
        }
    }
}
