package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.ResultBonus;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class ResultBonusCodec {
    public void encode(BinaryWriter output, ResultBonus bonus) {
        output.unsignedInt(ResultBonus.TYPE_ID).unsignedByte(bonus.kind()).signedInt(bonus.expBonus())
            .signedInt(bonus.goldBonus());
    }

    public ResultBonus decode(BinaryReader input) {
        if (input.unsignedInt() != ResultBonus.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_RESULT_BONUS_TYPE");
        }

        return new ResultBonus(input.unsignedByte(), (int) input.unsignedInt(), (int) input.unsignedInt());
    }
}
