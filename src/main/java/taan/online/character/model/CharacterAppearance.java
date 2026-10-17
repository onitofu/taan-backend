package taan.online.character.model;

import java.util.Comparator;
import java.util.List;

public record CharacterAppearance(List<AppearancePart> parts) {
    public CharacterAppearance {
        parts = parts.stream().sorted(Comparator.comparingInt(AppearancePart::slot)).toList();

        if (parts.size() != 16) {
            throw new IllegalArgumentException("Appearance requires sixteen parts");
        }

        for (int slot = 0; slot < 16; slot++) {
            if (parts.get(slot).slot() != slot) {
                throw new IllegalArgumentException("Appearance slots must be complete and unique");
            }
        }
    }
}
