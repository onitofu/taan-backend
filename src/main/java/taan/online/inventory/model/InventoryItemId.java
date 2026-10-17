package taan.online.inventory.model;

public record InventoryItemId(long value) {
    public InventoryItemId {
        if (value < 1) {
            throw new IllegalArgumentException("Invalid inventory identity");
        }
    }
}
