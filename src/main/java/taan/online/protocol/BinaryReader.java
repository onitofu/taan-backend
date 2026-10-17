package taan.online.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

public final class BinaryReader {
    private final ByteBuffer data;

    public BinaryReader(byte[] root, int expectedType) {
        data = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);

        if (unsignedInt() != Integer.toUnsignedLong(expectedType)) {
            throw new MalformedPacketException("UNEXPECTED_PACKET_TYPE");
        }
    }

    public int unsignedByte() {
        require(1);
        return Byte.toUnsignedInt(data.get());
    }

    public long unsignedInt() {
        require(4);
        return Integer.toUnsignedLong(data.getInt());
    }

    public String wideString() {
        long count = unsignedInt();

        if (count > data.remaining() / 2) {
            throw new MalformedPacketException("TRUNCATED_STRING");
        }

        int length = (int) count * 2;
        ByteBuffer text = data.slice(data.position(), length);
        data.position(data.position() + length);

        try {
            return StandardCharsets.UTF_16LE.newDecoder().decode(text).toString();
        } catch (CharacterCodingException failure) {
            throw new MalformedPacketException("INVALID_UTF16");
        }
    }

    public void finish() {
        if (data.hasRemaining()) {
            throw new MalformedPacketException("TRAILING_BYTES");
        }
    }

    private void require(int length) {
        if (data.remaining() < length) {
            throw new MalformedPacketException("TRUNCATED_PACKET");
        }
    }
}
