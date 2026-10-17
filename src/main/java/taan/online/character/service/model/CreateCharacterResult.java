package taan.online.character.service.model;

import java.util.List;
import java.util.Objects;
import taan.online.character.model.GameCharacter;

public record CreateCharacterResult(CreateCharacterOutcome outcome, List<GameCharacter> catalog) {
    public CreateCharacterResult {
        Objects.requireNonNull(outcome);
        catalog = List.copyOf(catalog);
    }
}
