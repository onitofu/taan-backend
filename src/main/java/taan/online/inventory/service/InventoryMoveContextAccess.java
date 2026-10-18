package taan.online.inventory.service;

import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;

public interface InventoryMoveContextAccess {
    InventoryMoveContext capture(InventoryActor actor);

    boolean current(InventoryMoveContext context);

    void equipmentChanged(InventoryMoveContext context, CharacterInventory equipment);
}
