package taan.online.character.model;

public record AppearancePart(int slot, long itemId, int parameter0, int parameter1, int parameter2) {
    public AppearancePart {
        if (slot < 0 || slot > 15 || itemId < 0 || itemId > 0xffff_ffffL) {
            throw new IllegalArgumentException("Appearance part is outside the wire range");
        }

        unsignedShort(parameter0);
        unsignedShort(parameter1);
        unsignedShort(parameter2);
    }

    private static void unsignedShort(int value) {
        if (value < 0 || value > 0xffff) {
            throw new IllegalArgumentException("Appearance parameter is outside the wire range");
        }
    }
}
