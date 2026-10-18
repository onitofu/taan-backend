package taan.online.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class BinaryPrimitiveTest {
    @Test
    void preservesSignedLittleEndianIntegersFloatsAndNegativeZeroAgainstLiteralBytes() {
        byte[] expected = HexFormat.of().parseHex("00000000ceffffff00000080ffffff7f0000a03f00000080");
        byte[] root = new BinaryWriter().unsignedInt(0).signedInt(-50).signedInt(Integer.MIN_VALUE)
            .signedInt(Integer.MAX_VALUE).float32(1.25f).float32(-0.0f).toByteArray();

        assertArrayEquals(expected, root);
        var input = new BinaryReader(expected, 0);

        assertEquals(-50, input.signedInt());
        assertEquals(Integer.MIN_VALUE, input.signedInt());
        assertEquals(Integer.MAX_VALUE, input.signedInt());
        assertEquals(1.25f, input.float32());
        assertEquals(Integer.MIN_VALUE, Float.floatToRawIntBits(input.float32()));
        input.finish();

        for (int length = 4; length < 8; length++) {
            byte[] truncated = Arrays.copyOf(root, length);

            assertThrows(MalformedPacketException.class, () -> new BinaryReader(truncated, 0).signedInt());
            assertThrows(MalformedPacketException.class, () -> new BinaryReader(truncated, 0).float32());
        }
    }
}
