package taan.online.channel.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.channel.model.ChannelParticipant;
import taan.online.character.model.CharacterProgressSnapshot;

public interface SelectedCharacterAccess {
    Optional<ChannelParticipant> selectedCharacter(UUID sessionId);

    default boolean refreshProgress(UUID sessionId, CharacterProgressSnapshot progress) {
        return false;
    }

    default boolean hasSelectedCharacter(UUID sessionId) {
        return selectedCharacter(sessionId).isPresent();
    }
}
