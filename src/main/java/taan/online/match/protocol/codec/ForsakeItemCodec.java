package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayForsakeItem;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class ForsakeItemCodec {
    public PcGamePlayForsakeItem decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayForsakeItem.TYPE_ID);
        var message = new PcGamePlayForsakeItem(input.unsignedByte(), input.unsignedByte(), input.unsignedShort());
        input.finish();
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.inventoryIndex(message.inventoryIndex());
        return message;
    }

    public byte[] encode(PcGamePlayForsakeItem message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.inventoryIndex(message.inventoryIndex());
        return new BinaryWriter().unsignedInt(PcGamePlayForsakeItem.TYPE_ID).unsignedByte(message.slotIndex())
            .unsignedByte(message.inventoryIndex()).unsignedShort(message.itemId()).toByteArray();
    }
}
