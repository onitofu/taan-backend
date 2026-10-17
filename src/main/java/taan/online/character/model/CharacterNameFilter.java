package taan.online.character.model;

import java.util.List;
import java.util.Locale;

public final class CharacterNameFilter {
    private final List<String> forbiddenSubstrings;

    public CharacterNameFilter(List<String> forbiddenSubstrings) {
        this.forbiddenSubstrings = forbiddenSubstrings.stream().map(
            value -> {
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Name filter entries must not be empty");
                }

                return value.toLowerCase(Locale.ROOT);
            }
        ).toList();
    }

    public boolean forbids(CharacterName name) {
        String lowered = name.value().toLowerCase(Locale.ROOT);
        return forbiddenSubstrings.stream().anyMatch(lowered::contains);
    }
}
