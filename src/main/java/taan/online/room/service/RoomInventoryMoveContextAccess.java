package taan.online.room.service;

import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.service.InventoryMoveContextAccess;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;

public final class RoomInventoryMoveContextAccess implements InventoryMoveContextAccess {
    private final RoomRegistry rooms;

    public RoomInventoryMoveContextAccess(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @Override
    public InventoryMoveContext capture(InventoryActor actor) {
        return rooms.inventoryContext(actor);
    }

    @Override
    public boolean current(InventoryMoveContext context) {
        return context.equals(capture(context.actor()));
    }

    @Override
    public void equipmentChanged(InventoryMoveContext context, CharacterInventory equipment) {
        rooms.refreshEquipment(context, equipment);
    }
}
