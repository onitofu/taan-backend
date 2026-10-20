package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class NotifyEatItemCodec {
    public PcGamePlayNotifyEatItem decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayNotifyEatItem.TYPE_ID);
        var message = new PcGamePlayNotifyEatItem(input.unsignedByte(), input.unsignedByte(), input.unsignedShort());
        input.finish();
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.inventoryIndex(message.inventoryIndex());
        return message;
    }

    public byte[] encode(PcGamePlayNotifyEatItem message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.inventoryIndex(message.inventoryIndex());
        return new BinaryWriter().unsignedInt(PcGamePlayNotifyEatItem.TYPE_ID).unsignedByte(message.slotIndex())
            .unsignedByte(message.inventoryIndex()).unsignedShort(message.itemId()).toByteArray();
    }
}
