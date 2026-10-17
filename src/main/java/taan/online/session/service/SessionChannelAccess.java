package taan.online.session.service;

import java.util.UUID;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.session.model.SessionId;

public final class SessionChannelAccess implements SelectedCharacterAccess {
    private final SessionRegistry sessions;

    public SessionChannelAccess(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean hasSelectedCharacter(UUID sessionId) {
        return sessions.selectedCharacter(new SessionId(sessionId)).isPresent();
    }
}
