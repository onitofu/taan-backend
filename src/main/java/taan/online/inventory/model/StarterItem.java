package taan.online.inventory.model;

public record StarterItem(int slot, long itemId, int parameter0, int parameter1, int parameter2) {
    public StarterItem {
        if (slot < 0 || slot >= 16 || itemId < 1 || itemId > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid starter item");
        }

        for (int parameter : new int[]{parameter0, parameter1, parameter2}) {
            if (parameter < 0 || parameter > 65535) {
                throw new IllegalArgumentException("Invalid item appearance parameter");
            }
        }
    }
}
