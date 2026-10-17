package taan.online.inventory.model;

import java.util.Objects;

public record ItemDefinition(
    long itemId,
    String name,
    Integer equipmentSlot,
    ItemGender gender,
    int defaultPalette1Id,
    int defaultPalette2Id,
    int defaultPalette3Id,
    boolean resourceAvailable
) {
    public ItemDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(gender);

        if (itemId < 1 || itemId > 0xffff_ffffL || name.isBlank()
            || equipmentSlot != null && (equipmentSlot < 0 || equipmentSlot > 15)) {
            throw new IllegalArgumentException("Invalid item definition");
        }

        for (int palette : new int[]{defaultPalette1Id, defaultPalette2Id, defaultPalette3Id}) {
            if (palette < 0 || palette > 65535) {
                throw new IllegalArgumentException("Invalid default item palette");
            }
        }
    }
}
