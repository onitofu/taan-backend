package taan.online.inventory.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import taan.online.inventory.model.InventoryMove;
import taan.online.inventory.model.InventoryMoveOutcome;
import taan.online.inventory.protocol.codec.InventoryUpdateCodec;
import taan.online.inventory.protocol.codec.MoveInventoryItemRequestCodec;
import taan.online.inventory.protocol.packet.PcUpdateInventory;
import taan.online.inventory.protocol.packet.PrMoveInventoryItem;
import taan.online.inventory.service.MoveInventoryItemAction;
import taan.online.inventory.service.SelectedInventoryAccess;
import taan.online.inventory.service.model.MoveInventoryItemCommand;

public final class MoveInventoryItemPacketHandler {
    private final MoveInventoryItemAction action;
    private final SelectedInventoryAccess sessions;

    public MoveInventoryItemPacketHandler(MoveInventoryItemAction action, SelectedInventoryAccess sessions) {
        this.action = action;
        this.sessions = sessions;
    }

    public InventoryHandlingResult handle(UUID id, byte[] root) {
        if (sessions.selected(id).isEmpty()) {
            return new InventoryHandlingResult(true, List.of());
        }

        var request = new MoveInventoryItemRequestCodec().decode(root);
        var result = action.execute(
            id,
            new MoveInventoryItemCommand(
                request.accountId(),
                new InventoryMove(
                    request.sourceInventoryId(),
                    request.instanceId(),
                    request.sourceSlot(),
                    request.sourceItemId(),
                    request.destinationInventoryId(),
                    request.destinationSlot(),
                    request.destinationObservedItemId()
                )
            )
        );

        if (result.outcome() == InventoryMoveOutcome.SESSION_CLOSED || sessions.selected(id).isEmpty()) {
            return new InventoryHandlingResult(true, List.of());
        }

        var codec = new InventoryUpdateCodec();
        var mapper = new InventoryMapper();
        var responses = new ArrayList<byte[]>();
        result.inventories().forEach(value -> responses.add(codec.update(new PcUpdateInventory(mapper.map(value)))));

        if (result.outcome() == InventoryMoveOutcome.MOVED) {
            responses.add(
                codec.moved(
                    new PrMoveInventoryItem(
                        request.sourceInventoryId(),
                        request.destinationInventoryId()
                    )
                )
            );
        }

        return new InventoryHandlingResult(false, responses);
    }
}
