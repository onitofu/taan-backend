package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.protocol.BinaryReader;

public final class ConfirmNameRequestCodec {
    public PqConfirmName decode(byte[] root) {
        var input = new BinaryReader(root, PqConfirmName.TYPE_ID);
        var request = new PqConfirmName(input.unsignedByte(), input.wideString());
        input.finish();
        return request;
    }
}
