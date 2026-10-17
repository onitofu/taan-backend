package taan.online.inventory.protocol.packet;

import java.util.List;

public record Inventory(long id, int capacity, List<InventoryItemData> items) {
    public static final int TYPE_ID = 0x123903ce;

    public Inventory {
        items = List.copyOf(items);
    }
}
