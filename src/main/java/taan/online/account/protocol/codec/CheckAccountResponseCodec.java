package taan.online.account.protocol.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import taan.online.account.protocol.packet.PrCheckAccount;

public final class CheckAccountResponseCodec {
    public byte[] encode(PrCheckAccount response) {
        return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putInt(PrCheckAccount.TYPE_ID)
            .put((byte) response.status()).put((byte) response.selector()).array();
    }
}
