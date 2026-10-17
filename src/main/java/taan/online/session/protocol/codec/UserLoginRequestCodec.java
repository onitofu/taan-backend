package taan.online.session.protocol.codec;

import java.util.ArrayList;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.protocol.packet.LoginAccountInfo;
import taan.online.session.protocol.packet.PqUserLogin;

public final class UserLoginRequestCodec {
    public PqUserLogin decode(byte[] root) {
        var reader = new BinaryReader(root, PqUserLogin.TYPE_ID);
        long count = reader.unsignedInt();

        if (count > reader.remaining() / 20) {
            throw new MalformedPacketException("INVALID_ACCOUNT_COUNT");
        }

        var accounts = new ArrayList<LoginAccountInfo>();

        for (long index = 0; index < count; index++) {
            if (reader.unsignedInt() != Integer.toUnsignedLong(LoginAccountInfo.TYPE_ID)) {
                throw new MalformedPacketException("INVALID_LOGIN_ACCOUNT_TYPE");
            }

            accounts.add(new LoginAccountInfo(reader.byteString(), reader.byteString()));
            reader.unsignedInt();
            reader.wideString();
        }

        reader.unsignedInt();
        reader.unsignedShort();
        reader.unsignedByte();
        reader.finish();
        return new PqUserLogin(accounts);
    }
}
