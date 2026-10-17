package taan.online.inventory.model;

public record StarterItem(int slot, long itemId, int palette1Id, int palette2Id, int palette3Id) {
    public StarterItem {
        if (slot < 0 || slot >= 16 || itemId < 1 || itemId > 0xffff_ffffL) {
            throw new IllegalArgumentException("Invalid starter item");
        }

        for (int parameter : new int[]{palette1Id, palette2Id, palette3Id}) {
            if (parameter < 0 || parameter > 65535) {
                throw new IllegalArgumentException("Invalid item appearance parameter");
            }
        }
    }
}
