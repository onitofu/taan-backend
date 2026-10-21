package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class UpdateMoneyCodec {
    public byte[] encode(PcUpdateMoney packet) {
        return new BinaryWriter().unsignedInt(PcUpdateMoney.TYPE_ID).unsignedByte(packet.localIndex())
            .unsignedByte(packet.kind()).unsignedInt(packet.balance()).unsignedInt(packet.limitMessageAmount())
            .unsignedByte(packet.showLimitMessage()).toByteArray();
    }

    public PcUpdateMoney decode(byte[] root) {
        var input = new BinaryReader(root, PcUpdateMoney.TYPE_ID);
        var packet = new PcUpdateMoney(
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedByte()
        );
        input.finish();
        return packet;
    }
}
