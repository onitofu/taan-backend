package taan.online.character.controller;

import java.util.Optional;
import java.util.UUID;
import taan.online.character.protocol.codec.ConfirmNameRequestCodec;
import taan.online.character.protocol.packet.PrConfirmName;
import taan.online.character.service.AuthenticatedAccountAccess;
import taan.online.character.service.ConfirmNameAction;
import taan.online.character.service.model.ConfirmNameResult;
import taan.online.protocol.MalformedPacketException;

public final class ConfirmNamePacketHandler {
    private final ConfirmNameAction action;
    private final AuthenticatedAccountAccess sessions;

    public ConfirmNamePacketHandler(ConfirmNameAction action, AuthenticatedAccountAccess sessions) {
        this.action = action;
        this.sessions = sessions;
    }

    public Optional<PrConfirmName> handle(UUID sessionId, byte[] root) {
        if (sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        var request = new ConfirmNameRequestCodec().decode(root);

        if (request.localSlot() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_LOCAL_ACCOUNT");
        }

        var result = action.execute(sessionId, request.name());

        if (result == ConfirmNameResult.SESSION_CLOSED || sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PrConfirmName(result == ConfirmNameResult.AVAILABLE ? 0 : 1));
    }
}
