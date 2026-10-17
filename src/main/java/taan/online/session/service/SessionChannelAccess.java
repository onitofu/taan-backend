package taan.online.session.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.session.model.SessionId;

public final class SessionChannelAccess implements SelectedCharacterAccess {
    private final SessionRegistry sessions;

    public SessionChannelAccess(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<ChannelParticipant> selectedCharacter(UUID sessionId) {
        return sessions.channelParticipant(new SessionId(sessionId));
    }
}
