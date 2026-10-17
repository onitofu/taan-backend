package taan.online.session.protocol.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import taan.online.session.protocol.packet.PrUserLogin;

public final class UserLoginResponseCodec {
    private static final int ACCOUNT_INFO_TYPE_ID = 0x19500459;
    private static final int CHARACTER_CATALOG_TYPE_ID = 0x343e0648;

    public byte[] encode(PrUserLogin response) {
        if (!response.success()) {
            return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN).putInt(PrUserLogin.TYPE_ID)
                .put((byte) 0).putInt(response.errorCode()).putLong(0).array();
        }

        return ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN).putInt(PrUserLogin.TYPE_ID)
            .put((byte) 1).putInt(0).putInt(0).putInt(1).putInt(ACCOUNT_INFO_TYPE_ID)
            .putInt((int) response.accountId()).putShort((short) 0).putInt(0)
            .put((byte) 0).put((byte) response.gender()).putInt(1).putInt(CHARACTER_CATALOG_TYPE_ID)
            .putInt(0).putInt(0).putShort((short) 0).putInt(0).put((byte) 0).array();
    }
}
