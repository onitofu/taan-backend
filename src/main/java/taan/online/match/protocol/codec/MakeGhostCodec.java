package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayMakeGhost;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class MakeGhostCodec {
    public byte[] encode(PcGamePlayMakeGhost message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlayMakeGhost.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).unsignedByte(message.facing()).toByteArray();
    }

    public PcGamePlayMakeGhost decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayMakeGhost.TYPE_ID);
        var message = new PcGamePlayMakeGhost(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlayMakeGhost message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.facing(message.facing());
    }
}
