package taan.online.inventory.controller;

import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.protocol.packet.Inventory;
import taan.online.inventory.protocol.packet.InventoryItemData;

public final class InventoryMapper {
    public Inventory map(CharacterInventory value) {
        return new Inventory(
            value.id().value(),
            value.kind().capacity(),
            value.items().stream().map(
                item -> new InventoryItemData(
                    item.id().value(),
                    item.slot(),
                    item.itemId(),
                    item.quantity(),
                    item.palette1Id(),
                    item.palette2Id(),
                    item.palette3Id()
                )
            ).toList()
        );
    }
}
