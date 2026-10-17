package taan.online.server;

import java.util.UUID;
import taan.online.inventory.controller.MoveInventoryItemPacketHandler;
import taan.online.inventory.protocol.packet.PqMoveInventoryItem;
import taan.online.protocol.PacketOutput;

public final class InventoryRequestDispatcher {
    private final MoveInventoryItemPacketHandler move;

    public InventoryRequestDispatcher(MoveInventoryItemPacketHandler move) {
        this.move = move;
    }

    RequestCompletion dispatch(UUID sessionId, byte[] root, PacketOutput output) {
        var result = move.handle(sessionId, root);
        return RequestCompletion.batch(
            output,
            result.ignored() ? ProtocolEvent.INVENTORY_REQUEST_IGNORED : ProtocolEvent.INVENTORY_REQUEST_COMPLETED,
            result.responses()
        );
    }

    static boolean accepts(int type) {
        return type == PqMoveInventoryItem.TYPE_ID;
    }
}
