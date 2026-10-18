package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.protocol.MalformedPacketException;

class PreviewActionCodecTest {
    @Test
    void consumesExactFixedLayoutsAndOnlyRecognizedOpaqueScenarioRoots() throws Exception {
        var codec = new UnsupportedGameplayCodec();

        for (int type : new int[]{0x2c3c05b9, 0x470b0762, 0x3f8506de}) {
            int size = type == 0x2c3c05b9 ? 14 : type == 0x470b0762 ? 18 : 11;
            byte[] root = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).putInt(type).array();
            assertEquals(type, codec.decode(type, root).typeId());
            assertTrue(UnsupportedGameplayCodec.accepts(type));

            for (int count = 0; count < root.length; count++) {
                byte[] truncated = Arrays.copyOf(root, count);
                assertThrows(MalformedPacketException.class, () -> codec.decode(type, truncated));
            }

            assertThrows(MalformedPacketException.class, () -> codec.decode(type, Arrays.copyOf(root, size + 1)));
        }

        assertEquals(0x46de0756, codec.decode(0x46de0756, MatchFixtures.root("scenario-native-c2s")).typeId());
        assertTrue(UnsupportedGameplayCodec.accepts(0x46de0756));
        assertFalse(UnsupportedGameplayCodec.accepts(0x57260801));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.decode(0x57260801, HexFormat.of().parseHex("01082657"))
        );
    }

    @Test
    void validatesEndScenarioWithoutApplyingItsClientValues() {
        var codec = new EndScenarioCodec();
        byte[] root = HexFormat.of().parseHex("6d080b5e0200");
        assertEquals(2, codec.decode(root).slotIndex());
        assertEquals(0, codec.decode(root).scalar11());

        for (int length = 0; length < root.length; length++) {
            byte[] truncated = Arrays.copyOf(root, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, 7)));
        root[4] = 8;
        assertThrows(MalformedPacketException.class, () -> codec.decode(root));
    }
}
