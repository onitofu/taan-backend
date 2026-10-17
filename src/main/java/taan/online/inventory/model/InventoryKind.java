package taan.online.inventory.model;

public enum InventoryKind {
    BAG(80), EQUIPMENT(16), JEWEL_BOX(0);

    private final int capacity;

    InventoryKind(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }
}
