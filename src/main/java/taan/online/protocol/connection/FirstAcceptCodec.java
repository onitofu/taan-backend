package taan.online.protocol.connection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FirstAcceptCodec {
    public byte[] encode(PcFirstAccept message) {
        return ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN).putInt(PcFirstAccept.TYPE_ID)
            .putLong(message.a()).putLong(message.b()).put((byte) message.shift()).array();
    }
}
