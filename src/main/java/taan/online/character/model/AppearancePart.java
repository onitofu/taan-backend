package taan.online.character.model;

public record AppearancePart(int slot, long itemId, int palette1Id, int palette2Id, int palette3Id) {
    public AppearancePart {
        if (slot < 0 || slot > 15 || itemId < 0 || itemId > 0xffff_ffffL) {
            throw new IllegalArgumentException("Appearance part is outside the wire range");
        }

        unsignedShort(palette1Id);
        unsignedShort(palette2Id);
        unsignedShort(palette3Id);
    }

    private static void unsignedShort(int value) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException("Appearance parameter is outside the wire range");
        }
    }
}
