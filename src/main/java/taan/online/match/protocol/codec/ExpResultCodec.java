package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.ExpResult;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class ExpResultCodec {
    public void encode(BinaryWriter output, ExpResult snapshot) {
        output.unsignedInt(ExpResult.TYPE_ID).unsignedShort(snapshot.level()).unsignedInt(snapshot.currentExp())
            .unsignedInt(snapshot.levelStartExp()).unsignedInt(snapshot.nextLevelExp());
    }

    public ExpResult decode(BinaryReader input) {
        if (input.unsignedInt() != ExpResult.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_EXP_RESULT_TYPE");
        }

        return new ExpResult(input.unsignedShort(), input.unsignedInt(), input.unsignedInt(), input.unsignedInt());
    }
}
