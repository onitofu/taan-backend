package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;
import taan.online.protocol.MalformedPacketException;

final class MatchWireFixtures {
    private MatchWireFixtures() {
    }

    static byte[] fixture(String name) throws IOException {
        try (var input = Objects.requireNonNull(MatchWireFixtures.class.getResourceAsStream("/match/" + name))) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    static <T> T golden(String name, Function<byte[], T> decoder, Function<T, byte[]> encoder) throws IOException {
        byte[] root = fixture(name);
        T message = decoder.apply(root);

        assertArrayEquals(root, encoder.apply(message));
        malformed(root, decoder);
        return message;
    }

    static void malformed(byte[] root, Function<byte[], ?> decoder) {
        for (int length = 0; length < root.length; length++) {
            byte[] truncated = Arrays.copyOf(root, length);

            assertThrows(MalformedPacketException.class, () -> decoder.apply(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> decoder.apply(Arrays.copyOf(root, root.length + 1)));
        byte[] wrongType = root.clone();
        wrongType[0] ^= 1;

        assertThrows(MalformedPacketException.class, () -> decoder.apply(wrongType));
    }

    static byte[] integer(byte[] root, int offset, int value) {
        byte[] result = root.clone();
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
        return result;
    }

    static byte[] octet(byte[] root, int offset, int value) {
        byte[] result = root.clone();
        result[offset] = (byte) value;
        return result;
    }
}
