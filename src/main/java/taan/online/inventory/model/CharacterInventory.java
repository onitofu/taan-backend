package taan.online.inventory.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record CharacterInventory(InventoryId id, InventoryKind kind, List<InventoryItem> items) {
    public CharacterInventory {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        items = items.stream().sorted(Comparator.comparingInt(InventoryItem::slot)).toList();

        if (items.stream().map(InventoryItem::slot).distinct().count() != items.size()) {
            throw new IllegalArgumentException("Duplicate inventory slot");
        }

        for (var item : items) {
            if (kind.capacity() != 0 && item.slot() >= kind.capacity()) {
                throw new IllegalArgumentException("Inventory slot exceeds capacity");
            }
        }
    }
}
