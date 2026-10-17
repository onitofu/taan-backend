package taan.online.character.service.model;

import java.util.Objects;
import taan.online.character.model.CharacterAppearance;

public record CreateCharacterCommand(String name, CharacterAppearance appearance) {
    public CreateCharacterCommand {
        Objects.requireNonNull(name);
        Objects.requireNonNull(appearance);
    }

    @Override
    public String toString() {
        return "CreateCharacterCommand[REDACTED]";
    }
}
