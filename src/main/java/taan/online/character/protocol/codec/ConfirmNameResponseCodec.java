package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PrConfirmName;
import taan.online.protocol.BinaryWriter;

public final class ConfirmNameResponseCodec {
    public byte[] encode(PrConfirmName response) {
        return new BinaryWriter().unsignedInt(PrConfirmName.TYPE_ID).unsignedByte(response.result()).toByteArray();
    }
}
