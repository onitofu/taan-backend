package taan.online.inventory.protocol.codec;

import taan.online.inventory.protocol.packet.Inventory;
import taan.online.protocol.BinaryWriter;

public final class InventoryCodec {
    public byte[] encode(Inventory inventory) {
        var output = new BinaryWriter().unsignedInt(Inventory.TYPE_ID).unsignedInt(inventory.id())
            .unsignedByte(0).unsignedByte(inventory.capacity()).unsignedInt(inventory.items().size());

        for (var item : inventory.items()) {
            output.unsignedInt(item.instanceId() & 0xffff_ffffL).unsignedInt(item.instanceId() >>> 32)
                .unsignedByte(item.slot()).unsignedInt(item.itemId()).unsignedShort(item.quantity())
                .unsignedByte(0).unsignedShort(65535).unsignedShort(0)
                .unsignedShort(item.palette1Id()).unsignedShort(item.palette2Id()).unsignedShort(item.palette3Id())
                .unsignedByte(0);
        }

        return output.toByteArray();
    }
}
