package taan.online.character.model;

import java.util.Objects;

public record CharacterName(String value) {
    public CharacterName {
        Objects.requireNonNull(value);

        for (int index = 0; index < value.length(); index++) {
            if (!allowed(value.charAt(index))) {
                throw new IllegalArgumentException("Unsupported character name symbol");
            }
        }

        if (value.length() < 4 || value.length() > 18) {
            throw new IllegalArgumentException("Character name length is outside the allowed range");
        }
    }

    private static boolean allowed(char value) {
        return value >= 0x0030 && value <= 0x0039
            || value >= 0x0041 && value <= 0x007a
            || value >= 0x00c0 && value <= 0x017f
            || value >= 0x0180 && value <= 0x0236
            || value >= 0x1ea0 && value <= 0x1ef9;
    }

    @Override
    public String toString() {
        return "CharacterName[REDACTED]";
    }
}
