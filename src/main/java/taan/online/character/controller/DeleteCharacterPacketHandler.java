package taan.online.character.controller;

import java.util.Optional;
import java.util.UUID;
import taan.online.character.protocol.codec.DeleteCharacterRequestCodec;
import taan.online.character.protocol.packet.PrDelCharacter;
import taan.online.character.service.AuthenticatedAccountAccess;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.model.DeleteCharacterOutcome;
import taan.online.protocol.MalformedPacketException;

public final class DeleteCharacterPacketHandler {
    private final DeleteCharacterAction action;
    private final AuthenticatedAccountAccess sessions;

    public DeleteCharacterPacketHandler(DeleteCharacterAction action, AuthenticatedAccountAccess sessions) {
        this.action = action;
        this.sessions = sessions;
    }

    public Optional<PrDelCharacter> handle(UUID sessionId, byte[] root) {
        if (sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        var request = new DeleteCharacterRequestCodec().decode(root);

        if (request.localSlot() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_LOCAL_ACCOUNT");
        }

        var result = action.execute(sessionId, request.characterId());

        if (result.outcome() == DeleteCharacterOutcome.SESSION_CLOSED
            || sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        int status = result.outcome() == DeleteCharacterOutcome.DELETED ? 0 : 1;
        var catalog = status == 0 ? new CharacterCatalogMapper().map(result.catalog()) : null;
        return Optional.of(new PrDelCharacter(status, 0, catalog));
    }
}
