package taan.online.inventory.service;

import java.util.ArrayList;
import java.util.List;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;

public final class TestInventoryMoveContextAccess implements InventoryMoveContextAccess {
    public boolean allowed = true;
    public boolean current = true;
    public Runnable onCapture = () -> { };
    public Runnable onCurrent = () -> { };
    public Runnable onEquipmentChanged = () -> { };
    public final List<CharacterInventory> equipment = new ArrayList<>();

    @Override
    public InventoryMoveContext capture(InventoryActor actor) {
        onCapture.run();
        return new InventoryMoveContext(actor, null, allowed);
    }

    @Override
    public boolean current(InventoryMoveContext context) {
        onCurrent.run();
        return current;
    }

    @Override
    public void equipmentChanged(InventoryMoveContext context, CharacterInventory snapshot) {
        equipment.add(snapshot);
        onEquipmentChanged.run();
    }
}
