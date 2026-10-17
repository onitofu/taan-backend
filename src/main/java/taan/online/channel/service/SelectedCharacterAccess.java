package taan.online.channel.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.channel.model.ChannelParticipant;

public interface SelectedCharacterAccess {
    Optional<ChannelParticipant> selectedCharacter(UUID sessionId);

    default boolean hasSelectedCharacter(UUID sessionId) {
        return selectedCharacter(sessionId).isPresent();
    }
}
