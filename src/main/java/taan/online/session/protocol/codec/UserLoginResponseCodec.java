package taan.online.session.protocol.codec;

import taan.online.character.protocol.codec.CharacterCatalogCodec;
import taan.online.protocol.BinaryWriter;
import taan.online.session.protocol.packet.PrUserLogin;

public final class UserLoginResponseCodec {
    private static final int ACCOUNT_INFO_TYPE_ID = 0x19500459;

    public byte[] encode(PrUserLogin response) {
        var output = new BinaryWriter().unsignedInt(PrUserLogin.TYPE_ID);

        if (!response.success()) {
            return output.unsignedByte(0).unsignedInt(response.errorCode())
                .unsignedInt(0).unsignedInt(0).toByteArray();
        }

        return output.unsignedByte(1).unsignedInt(0).unsignedInt(0).unsignedInt(1).unsignedInt(ACCOUNT_INFO_TYPE_ID)
            .unsignedInt(response.accountId()).unsignedShort(0).unsignedInt(0)
            .unsignedByte(0).unsignedByte(response.gender()).unsignedInt(1)
            .bytes(new CharacterCatalogCodec().encode(response.characterCatalog()))
            .unsignedShort(0).wideString("").unsignedByte(0).toByteArray();
    }
}
