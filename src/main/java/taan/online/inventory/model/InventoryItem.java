package taan.online.inventory.model;

import java.util.Objects;

public record InventoryItem(
    InventoryItemId id,
    int slot,
    long itemId,
    int quantity,
    int parameter0,
    int parameter1,
    int parameter2
) {
    public InventoryItem {
        Objects.requireNonNull(id);

        if (slot < 0 || slot > 255 || itemId < 1 || itemId > 0xffff_ffffL || quantity < 1 || quantity > 65535) {
            throw new IllegalArgumentException("Invalid inventory item");
        }

        for (int parameter : new int[]{parameter0, parameter1, parameter2}) {
            if (parameter < 0 || parameter > 65535) {
                throw new IllegalArgumentException("Invalid item appearance parameter");
            }
        }
    }
}
