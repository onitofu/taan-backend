package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlaySetGhostTarget;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class SetGhostTargetCodec {
    public byte[] encode(PcGamePlaySetGhostTarget message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlaySetGhostTarget.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).toByteArray();
    }

    public PcGamePlaySetGhostTarget decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlaySetGhostTarget.TYPE_ID);
        var message = new PcGamePlaySetGhostTarget(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlaySetGhostTarget message) {
        MatchWireChecks.slot(message.slotIndex());
    }
}
