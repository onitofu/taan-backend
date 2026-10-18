package taan.online.protocol.option.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.option.packet.OptionList;

class OptionListCodecTest {
    @Test
    void encodesTheIndependentEmptyTypedContainerAndRejectsEveryTruncation() throws Exception {
        byte[] expected;

        try (var stream = getClass().getResourceAsStream("/match/empty-option-list.typed.hex")) {
            expected = HexFormat.of().parseHex(
                new String(stream.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }

        var codec = new OptionListCodec();
        var output = new BinaryWriter();
        codec.encode(output, new OptionList());

        assertArrayEquals(expected, output.toByteArray());
        var input = reader(expected);

        assertEquals(new OptionList(), codec.decode(input));
        input.finish();

        for (int length = 0; length < expected.length; length++) {
            byte[] truncated = Arrays.copyOf(expected, length);

            assertThrows(MalformedPacketException.class, () -> codec.decode(reader(truncated)));
        }

        assertThrows(NullPointerException.class, () -> codec.encode(new BinaryWriter(), null));
    }

    @Test
    void rejectsUnsupportedValuesWithoutExposingExternalContentsOrLoggingThem() {
        var codec = new OptionListCodec();

        try (var logs = new LogCapture()) {
            var valid = reader(new BinaryWriter().unsignedInt(OptionList.TYPE_ID).unsignedInt(0).toByteArray());

            assertEquals(new OptionList(), codec.decode(valid));
            byte[] wrongType = new BinaryWriter().unsignedInt(0).wideString(TestFixtures.CREDENTIAL).toByteArray();
            byte[] nonempty = new BinaryWriter().unsignedInt(OptionList.TYPE_ID).unsignedInt(0xffffffffL)
                .wideString(TestFixtures.LOGIN).toByteArray();
            var failure = assertThrows(MalformedPacketException.class, () -> codec.decode(reader(wrongType)));
            var unsupported = assertThrows(MalformedPacketException.class, () -> codec.decode(reader(nonempty)));

            assertFalse(failure.getMessage().contains(TestFixtures.CREDENTIAL));
            assertFalse(unsupported.getMessage().contains(TestFixtures.LOGIN));
            assertTrue(logs.text().isEmpty());
        }
    }

    private BinaryReader reader(byte[] typed) {
        return new BinaryReader(new BinaryWriter().unsignedInt(0).bytes(typed).toByteArray(), 0);
    }
}
