package taan.online.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class BinaryWriterTest {
    @Test
    void writesIndependentLittleEndianBytesAndUtf16UnitCounts() {
        var writer = new BinaryWriter().unsignedByte(255).unsignedShort(0xabcd).unsignedInt(0xfedcba98L)
            .wideString("Á😀").bytes(new byte[]{0x12});
        byte[] expected = HexFormat.of().parseHex("ffcdab98badcfe03000000c1003dd800de12");

        assertArrayEquals(expected, writer.toByteArray());

        writer.toByteArray()[0] = 0;

        assertEquals(255, Byte.toUnsignedInt(writer.toByteArray()[0]));
        assertThrows(IllegalArgumentException.class, () -> writer.wideString("\ud800"));
    }

    @Test
    void rejectsValuesThatWouldBeSilentlyTruncatedOnTheWire() {
        var writer = new BinaryWriter();

        for (int value : new int[]{-1, 256}) {
            assertThrows(IllegalArgumentException.class, () -> writer.unsignedByte(value));
        }

        for (int value : new int[]{-1, 65536}) {
            assertThrows(IllegalArgumentException.class, () -> writer.unsignedShort(value));
        }

        for (long value : new long[]{-1, 0x1_0000_0000L}) {
            assertThrows(IllegalArgumentException.class, () -> writer.unsignedInt(value));
        }
    }
}
