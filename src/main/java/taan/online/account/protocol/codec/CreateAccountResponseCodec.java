package taan.online.account.protocol.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import taan.online.account.protocol.packet.PrCreateAccount;

public final class CreateAccountResponseCodec {
    public byte[] encode(PrCreateAccount response) {
        return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putInt(PrCreateAccount.TYPE_ID)
            .put((byte) response.status()).put((byte) response.selector()).array();
    }
}
