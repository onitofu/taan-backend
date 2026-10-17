package taan.online.session.model;

import java.util.Objects;
import taan.online.account.model.AccountGender;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;

public record SelectedCharacterProfile(CharacterId id, CharacterName name, int level, AccountGender gender) {
    public SelectedCharacterProfile {
        Objects.requireNonNull(gender);
    }
}
