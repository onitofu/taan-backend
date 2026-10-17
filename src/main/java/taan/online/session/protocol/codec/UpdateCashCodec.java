package taan.online.session.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.session.protocol.packet.PcUpdateCash;

public final class UpdateCashCodec {
    public PcUpdateCash decode(byte[] root) {
        var input = new BinaryReader(root, PcUpdateCash.TYPE_ID);
        var request = new PcUpdateCash(input.unsignedInt(), input.unsignedInt());
        input.finish();
        return request;
    }

    public byte[] encode(PcUpdateCash packet) {
        return new BinaryWriter().unsignedInt(PcUpdateCash.TYPE_ID).unsignedInt(packet.balance())
            .unsignedInt(packet.localSlot()).toByteArray();
    }
}
