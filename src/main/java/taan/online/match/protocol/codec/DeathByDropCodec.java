package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayDeathByDrop;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class DeathByDropCodec {
    public byte[] encode(PcGamePlayDeathByDrop message) {
        validate(message);
        return new BinaryWriter().unsignedInt(PcGamePlayDeathByDrop.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.x()).signedInt(message.y()).toByteArray();
    }

    public PcGamePlayDeathByDrop decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayDeathByDrop.TYPE_ID);
        var message = new PcGamePlayDeathByDrop(
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGamePlayDeathByDrop message) {
        MatchWireChecks.slot(message.slotIndex());
    }
}
