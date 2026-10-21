package taan.online.channel.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChannelLootDefinitionTest {
    @Test
    void acceptsOnlyTheConfiguredNativeRange() {
        for (int scalar : new int[]{0, 49, 50, 69, 70, 89, 90, 100}) {
            assertEquals(scalar, new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, scalar).lootPickerScalar());
        }

        for (int scalar : new int[]{-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(
                IllegalArgumentException.class,
                () -> new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, scalar)
            );
        }
    }
}
