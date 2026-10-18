package taan.online.room.service;

import java.util.ArrayList;
import java.util.stream.IntStream;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.model.InventoryKind;

public final class RoomAppearanceMapper {
    public CharacterAppearance map(CharacterInventory equipment) {
        if (equipment.kind() != InventoryKind.EQUIPMENT) {
            throw new IllegalArgumentException("Appearance requires an equipment inventory");
        }

        var parts = new ArrayList<>(
            IntStream.range(0, 16).mapToObj(slot -> new AppearancePart(slot, 0, 0, 0, 0)).toList()
        );

        for (var item : equipment.items()) {
            parts.set(
                item.slot(),
                new AppearancePart(item.slot(), item.itemId(), item.palette1Id(), item.palette2Id(), item.palette3Id())
            );
        }

        return new CharacterAppearance(parts);
    }
}
