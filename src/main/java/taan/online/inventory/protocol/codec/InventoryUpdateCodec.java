package taan.online.inventory.protocol.codec;

import taan.online.inventory.protocol.packet.PcUpdateInventory;
import taan.online.inventory.protocol.packet.PrMoveInventoryItem;
import taan.online.protocol.BinaryWriter;

public final class InventoryUpdateCodec {
    public byte[] update(PcUpdateInventory response) {
        return new BinaryWriter().unsignedInt(PcUpdateInventory.TYPE_ID).unsignedByte(0)
            .bytes(new InventoryCodec().encode(response.inventory())).toByteArray();
    }

    public byte[] moved(PrMoveInventoryItem response) {
        return new BinaryWriter().unsignedInt(PrMoveInventoryItem.TYPE_ID).unsignedInt(response.sourceInventoryId())
            .unsignedInt(response.destinationInventoryId()).toByteArray();
    }
}
