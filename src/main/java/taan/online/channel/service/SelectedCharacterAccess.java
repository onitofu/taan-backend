package taan.online.channel.service;

import java.util.UUID;

public interface SelectedCharacterAccess {
    boolean hasSelectedCharacter(UUID sessionId);
}
