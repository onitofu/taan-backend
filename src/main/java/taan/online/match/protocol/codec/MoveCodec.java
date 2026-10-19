package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class MoveCodec {
    public byte[] encode(PcGamePlayMove message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlayMove.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).unsignedByte(message.facing()).toByteArray();
    }

    public PcGamePlayMove decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayMove.TYPE_ID);
        var message = new PcGamePlayMove(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlayMove message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.facing(message.facing());
    }
}
