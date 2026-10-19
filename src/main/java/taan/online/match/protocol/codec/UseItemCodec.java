package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class UseItemCodec {
    public byte[] encode(PcGamePlayUseItem message) {
        MatchWireChecks.slot(message.slotIndex());
        return new BinaryWriter().unsignedInt(PcGamePlayUseItem.TYPE_ID).unsignedByte(message.slotIndex())
            .unsignedByte(message.team()).unsignedByte(message.flag12()).unsignedByte(message.flag13())
            .unsignedByte(message.inventoryIndex()).unsignedShort(message.itemId()).toByteArray();
    }

    public PcGamePlayUseItem decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayUseItem.TYPE_ID);
        var message = new PcGamePlayUseItem(
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedByte(),
            input.unsignedShort()
        );
        input.finish();
        MatchWireChecks.slot(message.slotIndex());
        return message;
    }
}
