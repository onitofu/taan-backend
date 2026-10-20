package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PqGamePlayMakeGhost;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class MakeGhostRequestCodec {
    public PqGamePlayMakeGhost decode(byte[] root) {
        var input = new BinaryReader(root, PqGamePlayMakeGhost.TYPE_ID);
        var message = new PqGamePlayMakeGhost(input.unsignedByte());
        input.finish();
        MatchWireChecks.slot(message.slotIndex());
        return message;
    }

    public byte[] encode(PqGamePlayMakeGhost message) {
        MatchWireChecks.slot(message.slotIndex());
        return new BinaryWriter().unsignedInt(PqGamePlayMakeGhost.TYPE_ID)
            .unsignedByte(message.slotIndex()).toByteArray();
    }
}
