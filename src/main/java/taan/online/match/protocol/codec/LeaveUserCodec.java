package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayLeaveUser;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class LeaveUserCodec {
    public byte[] encode(PcGamePlayLeaveUser message) {
        MatchWireChecks.slot(message.slotIndex());
        return new BinaryWriter().unsignedInt(PcGamePlayLeaveUser.TYPE_ID).unsignedByte(message.slotIndex())
            .toByteArray();
    }

    public PcGamePlayLeaveUser decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayLeaveUser.TYPE_ID);
        int slot = input.unsignedByte();
        input.finish();
        MatchWireChecks.slot(slot);
        return new PcGamePlayLeaveUser(slot);
    }
}
