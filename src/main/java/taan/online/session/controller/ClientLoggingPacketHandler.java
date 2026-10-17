package taan.online.session.controller;

import java.util.UUID;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.model.SessionId;
import taan.online.session.protocol.codec.ClientLoggingCodec;
import taan.online.session.service.SessionRegistry;

public final class ClientLoggingPacketHandler {
    private final SessionRegistry sessions;

    public ClientLoggingPacketHandler(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    public boolean handle(UUID sessionId, byte[] root) {
        var selected = sessions.selectedCharacter(new SessionId(sessionId));

        if (selected.isEmpty()) {
            return false;
        }

        var request = new ClientLoggingCodec().decode(root);

        if (request.characterId() != selected.orElseThrow().value()) {
            throw new MalformedPacketException("INVALID_CHARACTER_CONTEXT");
        }

        return true;
    }
}
