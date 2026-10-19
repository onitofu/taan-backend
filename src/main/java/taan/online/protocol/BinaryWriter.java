package taan.online.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

public final class BinaryWriter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    public BinaryWriter unsignedByte(int value) {
        if (value < 0 || value > 0xff) {
            throw new IllegalArgumentException("Value is outside the unsigned byte range");
        }

        output.write(value);
        return this;
    }

    public BinaryWriter unsignedShort(int value) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException("Value is outside the unsigned short range");
        }

        output.write(value);
        output.write(value >>> 8);
        return this;
    }

    public BinaryWriter unsignedInt(long value) {
        if (value < 0 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Value is outside the unsigned integer range");
        }

        for (int shift = 0; shift < 32; shift += 8) {
            output.write((int) (value >>> shift));
        }

        return this;
    }

    public BinaryWriter bytes(byte[] value) {
        output.writeBytes(value);
        return this;
    }

    public BinaryWriter signedInt(int value) {
        return unsignedInt(Integer.toUnsignedLong(value));
    }

    public BinaryWriter signedLong(long value) {
        signedInt((int) value);
        return signedInt((int) (value >>> 32));
    }

    public BinaryWriter float32(float value) {
        return signedInt(Float.floatToRawIntBits(value));
    }

    public BinaryWriter wideString(String value) {
        try {
            var encoded = StandardCharsets.UTF_16LE.newEncoder().encode(CharBuffer.wrap(value));
            var bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return unsignedInt(value.length()).bytes(bytes);
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Invalid UTF-16 string");
        }
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }
}
