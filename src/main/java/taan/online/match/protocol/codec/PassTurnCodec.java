package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class PassTurnCodec {
    public byte[] encode(PcGamePlayPassTurn message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlayPassTurn.TYPE_ID)
            .unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).unsignedByte(message.facing())
            .unsignedInt(message.scalar20())
            .toByteArray();
    }

    public PcGamePlayPassTurn decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayPassTurn.TYPE_ID);
        var message = new PcGamePlayPassTurn(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte(),
            input.unsignedInt()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlayPassTurn message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.facing(message.facing());
    }
}
