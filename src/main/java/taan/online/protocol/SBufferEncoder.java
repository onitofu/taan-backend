package taan.online.protocol;

public final class SBufferEncoder {
    public byte[] encode(byte[] data) {
        long first = 0;
        long second = 0;

        for (byte value : data) {
            first = (first + Byte.toUnsignedInt(value)) % 65521;
            second = (second + first) % 65521;
        }

        return new BinaryWriter().unsignedByte(0x53).unsignedByte(0)
            .unsignedInt(second << 16 | first).bytes(data).toByteArray();
    }
}
