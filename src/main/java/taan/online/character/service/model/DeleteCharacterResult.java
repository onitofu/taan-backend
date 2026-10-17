package taan.online.character.service.model;

import java.util.List;
import java.util.Objects;
import taan.online.character.model.GameCharacter;

public record DeleteCharacterResult(DeleteCharacterOutcome outcome, List<GameCharacter> catalog) {
    public DeleteCharacterResult {
        Objects.requireNonNull(outcome);
        catalog = List.copyOf(catalog);
    }
}
