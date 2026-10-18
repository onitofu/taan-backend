package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class SetPosCodec {
    public byte[] encode(PcGamePlaySetPos message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlaySetPos.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).unsignedByte(message.facing()).toByteArray();
    }

    public PcGamePlaySetPos decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlaySetPos.TYPE_ID);
        var message = new PcGamePlaySetPos(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlaySetPos message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.facing(message.facing());
    }
}
