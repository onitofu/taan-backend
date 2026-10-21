package taan.online.character.model;

import java.util.Objects;

public record CharacterProgressSnapshot(CharacterId characterId, int level, CharacterProgress progress) {
    public CharacterProgressSnapshot {
        Objects.requireNonNull(characterId);
        Objects.requireNonNull(progress);

        if (level < 1 || level > 109) {
            throw new IllegalArgumentException("Invalid progression level");
        }
    }
}
