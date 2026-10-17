package taan.online.protocol.connection.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;

class ConnectionPacketCodecTest {
    @Test
    void validatesHandshakeAndKeepAliveWithoutRetainingClientMetadata() {
        byte[] first = TestFixtures.fixture("first-packet-minimal.root.hex");
        var codec = new FirstPacketRequestCodec();
        codec.decode(first);

        for (int length = 0; length < first.length; length++) {
            byte[] truncated = Arrays.copyOf(first, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(first, first.length + 1)));
        new KeepAliveCodec().decode(new byte[]{0x37, 0x04, (byte) 0x89, 0x18});
        assertThrows(
            MalformedPacketException.class,
            () -> new KeepAliveCodec().decode(new byte[]{0x37, 0x04, (byte) 0x89, 0x18, 0})
        );
    }

    @Test
    void boundsOptionalBuffersAndByteStringsBeforeAllocating() {
        byte[] first = TestFixtures.fixture("first-packet-minimal.root.hex");
        byte[] withBuffer = new byte[first.length + 7];
        System.arraycopy(first, 0, withBuffer, 0, 17);
        withBuffer[17] = 1;
        ByteBuffer.wrap(withBuffer).order(ByteOrder.LITTLE_ENDIAN).putInt(18, 3);
        System.arraycopy(first, 18, withBuffer, 25, first.length - 18);
        new FirstPacketRequestCodec().decode(withBuffer);

        ByteBuffer.wrap(withBuffer).order(ByteOrder.LITTLE_ENDIAN).putInt(18, -1);

        assertThrows(MalformedPacketException.class, () -> new FirstPacketRequestCodec().decode(withBuffer));
        assertThrows(
            MalformedPacketException.class,
            () -> new BinaryReader(
                new byte[]{0, 0, 0, 0, -1, -1, -1, -1},
                0
            ).byteString()
        );
    }

    @Test
    void encodesUtcTimeAgainstIndependentFixtureAndFloorsFourSecondTicks() {
        var codec = new FirstPacketResponseCodec();
        byte[] expected = TestFixtures.fixture("first-packet-fixed-time.root.hex");
        var reader = new BinaryReader(expected, 0x231a0522);
        reader.unsignedByte();
        int days = reader.unsignedShort();
        int ticks = reader.unsignedShort();
        Instant instant = Instant.parse("1900-01-01T00:00:00Z").plusSeconds(days * 86400L + ticks * 4L);

        assertArrayEquals(expected, codec.encode(Clock.fixed(instant.plusSeconds(3), ZoneOffset.UTC)));
        assertArrayEquals(
            new byte[]{0x22, 0x05, 0x1a, 0x23, 0, 0, 0, 0, 0, 0},
            codec.encode(Clock.fixed(Instant.parse("1900-01-01T00:00:03Z"), ZoneOffset.ofHours(7)))
        );
        assertEquals(10, expected.length);
    }
}
