package taan.online.character.service.model;

import java.util.List;
import java.util.Objects;
import taan.online.character.model.GameCharacter;
import taan.online.inventory.model.CharacterInventory;

public record SelectCharacterResult(
    SelectCharacterOutcome outcome,
    GameCharacter character,
    List<CharacterInventory> inventories
) {
    public SelectCharacterResult {
        Objects.requireNonNull(outcome);
        inventories = List.copyOf(inventories);
    }
}
