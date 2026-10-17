package taan.online.channel.protocol.codec;

import taan.online.channel.protocol.packet.PqChannelMoveTo;
import taan.online.protocol.BinaryReader;

public final class MoveToRequestCodec {
    public PqChannelMoveTo decode(byte[] root) {
        var input = new BinaryReader(root, PqChannelMoveTo.TYPE_ID);
        var request = new PqChannelMoveTo(
            input.unsignedByte(),
            input.unsignedShort(),
            input.unsignedInt(),
            input.unsignedInt()
        );
        input.finish();
        return request;
    }
}
