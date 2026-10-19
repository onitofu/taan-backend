package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.SpentItem;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class SpendItemCodec {
    public byte[] encode(PcGamePlaySpendItem message) {
        MatchWireChecks.slot(message.slotIndex());
        MatchWireChecks.count(message.items().size(), 3, 9, 3);
        var output = new BinaryWriter().unsignedInt(PcGamePlaySpendItem.TYPE_ID).unsignedByte(message.slotIndex())
            .unsignedByte(message.teamClear11()).unsignedByte(message.teamClear12())
            .unsignedInt(message.items().size());
        message.items().forEach(item -> output.unsignedShort(item.itemId()).unsignedByte(item.scalar2()));
        return output.toByteArray();
    }

    public PcGamePlaySpendItem decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlaySpendItem.TYPE_ID);
        int slot = input.unsignedByte();
        MatchWireChecks.slot(slot);
        int first = input.unsignedByte();
        int second = input.unsignedByte();
        int count = MatchWireChecks.count(input.unsignedInt(), 3, input.remaining(), 3);
        var items = new ArrayList<SpentItem>();

        for (int index = 0; index < count; index++) {
            items.add(new SpentItem(input.unsignedShort(), input.unsignedByte()));
        }

        input.finish();
        return new PcGamePlaySpendItem(slot, first, second, items);
    }
}
