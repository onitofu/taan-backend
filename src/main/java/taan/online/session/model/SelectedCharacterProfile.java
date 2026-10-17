package taan.online.session.model;

import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;

public record SelectedCharacterProfile(CharacterId id, CharacterName name, int level) {
}
