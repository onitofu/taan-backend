package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PcUpdateExp;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class UpdateExpCodec {
    public byte[] encode(PcUpdateExp message) {
        return new BinaryWriter().unsignedInt(PcUpdateExp.TYPE_ID).unsignedByte(message.localIndex())
            .unsignedShort(message.level()).unsignedInt(message.currentExp()).unsignedInt(message.levelStartExp())
            .unsignedInt(message.nextLevelExp()).toByteArray();
    }

    public PcUpdateExp decode(byte[] root) {
        var input = new BinaryReader(root, PcUpdateExp.TYPE_ID);
        var result = new PcUpdateExp(
            input.unsignedByte(),
            input.unsignedShort(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedInt()
        );
        input.finish();
        return result;
    }
}
