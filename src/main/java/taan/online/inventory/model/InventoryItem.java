package taan.online.inventory.model;

import java.util.Objects;

public record InventoryItem(
    InventoryItemId id,
    int slot,
    long itemId,
    int quantity,
    int palette1Id,
    int palette2Id,
    int palette3Id
) {
    public InventoryItem {
        Objects.requireNonNull(id);

        if (slot < 0 || slot > 255 || itemId < 1 || itemId > 0xffff_ffffL || quantity < 1 || quantity > 65535) {
            throw new IllegalArgumentException("Invalid inventory item");
        }

        for (int parameter : new int[]{palette1Id, palette2Id, palette3Id}) {
            if (parameter < 0 || parameter > 65535) {
                throw new IllegalArgumentException("Invalid item appearance parameter");
            }
        }
    }
}
