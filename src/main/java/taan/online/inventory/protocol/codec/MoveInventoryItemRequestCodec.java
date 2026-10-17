package taan.online.inventory.protocol.codec;

import taan.online.inventory.protocol.packet.PqMoveInventoryItem;
import taan.online.protocol.BinaryReader;

public final class MoveInventoryItemRequestCodec {
    public PqMoveInventoryItem decode(byte[] root) {
        var input = new BinaryReader(root, PqMoveInventoryItem.TYPE_ID);
        long account = input.unsignedInt();
        long source = input.unsignedInt();
        long instance = input.unsignedInt() | input.unsignedInt() << 32;
        var request = new PqMoveInventoryItem(
            account,
            source,
            instance,
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedByte(),
            input.unsignedInt()
        );
        input.finish();
        return request;
    }
}
