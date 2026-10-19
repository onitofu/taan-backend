package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayTurnEnd;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class TurnEndCodec {
    public byte[] encode(PcGamePlayTurnEnd message) {
        MatchWireChecks.slot(message.slotIndex());
        return new BinaryWriter().signedInt(PcGamePlayTurnEnd.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.scalar14()).unsignedByte(0).toByteArray();
    }

    public PcGamePlayTurnEnd decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayTurnEnd.TYPE_ID);
        var message = new PcGamePlayTurnEnd(input.unsignedByte(), input.signedInt());
        MatchWireChecks.slot(message.slotIndex());

        if (input.unsignedByte() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_TURN_END_EFFECT");
        }

        input.finish();
        return message;
    }
}
