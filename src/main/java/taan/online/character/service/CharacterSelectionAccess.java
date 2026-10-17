package taan.online.character.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.CharacterId;
import taan.online.character.model.GameCharacter;

public interface CharacterSelectionAccess extends AuthenticatedAccountAccess {
    boolean trySelect(UUID sessionId, GameCharacter character);

    Optional<CharacterId> selectedCharacter(UUID sessionId);
}
