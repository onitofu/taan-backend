package taan.online.inventory.model;

public record InventoryId(long value) {
    public InventoryId {
        if (value < 1 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid inventory identity");
        }
    }
}
