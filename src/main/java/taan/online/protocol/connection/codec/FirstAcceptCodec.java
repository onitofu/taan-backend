package taan.online.protocol.connection.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import taan.online.protocol.connection.packet.PcFirstAccept;

public final class FirstAcceptCodec {
    public byte[] encode(PcFirstAccept message) {
        return ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN).putInt(PcFirstAccept.TYPE_ID)
            .putLong(message.a()).putLong(message.b()).put((byte) message.shift()).array();
    }
}
