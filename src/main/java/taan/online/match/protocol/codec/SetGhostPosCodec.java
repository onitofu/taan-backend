package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlaySetGhostPos;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class SetGhostPosCodec {
    public byte[] encode(PcGamePlaySetGhostPos message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlaySetGhostPos.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).unsignedByte(message.facing()).toByteArray();
    }

    public PcGamePlaySetGhostPos decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlaySetGhostPos.TYPE_ID);
        var message = new PcGamePlaySetGhostPos(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlaySetGhostPos message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.facing(message.facing());
    }
}
