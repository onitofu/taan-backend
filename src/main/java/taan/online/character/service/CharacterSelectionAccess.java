package taan.online.character.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.CharacterId;

public interface CharacterSelectionAccess extends AuthenticatedAccountAccess {
    boolean trySelect(UUID sessionId, CharacterId characterId);

    Optional<CharacterId> selectedCharacter(UUID sessionId);
}
