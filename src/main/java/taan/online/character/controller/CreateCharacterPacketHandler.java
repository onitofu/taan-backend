package taan.online.character.controller;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.protocol.codec.CreateCharacterRequestCodec;
import taan.online.character.protocol.packet.PrNewCharacter;
import taan.online.character.service.AuthenticatedAccountAccess;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.model.CreateCharacterCommand;
import taan.online.character.service.model.CreateCharacterOutcome;
import taan.online.protocol.MalformedPacketException;

public final class CreateCharacterPacketHandler {
    private final CharacterLevelTable levels;

    private final CreateCharacterAction action;
    private final AuthenticatedAccountAccess sessions;

    public CreateCharacterPacketHandler(
        CreateCharacterAction action,
        AuthenticatedAccountAccess sessions,
        CharacterLevelTable levels
    ) {
        this.levels = levels;
        this.action = action;
        this.sessions = sessions;
    }

    public Optional<PrNewCharacter> handle(UUID sessionId, byte[] root) {
        if (sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        var request = new CreateCharacterRequestCodec().decode(root);

        if (request.localSlot() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_LOCAL_ACCOUNT");
        }

        var parts = new ArrayList<AppearancePart>();

        for (int slot = 0; slot < request.parts().size(); slot++) {
            var part = request.parts().get(slot);
            parts.add(new AppearancePart(slot, part.itemId(), part.palette1Id(), part.palette2Id(), part.palette3Id()));
        }

        var result = action.execute(
            sessionId,
            new CreateCharacterCommand(request.name(), new CharacterAppearance(parts))
        );

        if (result.outcome() == CreateCharacterOutcome.SESSION_CLOSED
            || sessions.authenticatedAccount(sessionId).isEmpty()) {
            return Optional.empty();
        }

        int status = switch (result.outcome()) {
            case CREATED -> 0;
            case NAME_TAKEN -> 1;
            default -> 2;
        };
        var catalog = status == 0 ? new CharacterCatalogMapper(levels).map(result.catalog()) : null;
        return Optional.of(new PrNewCharacter(status, 0, catalog));
    }
}
