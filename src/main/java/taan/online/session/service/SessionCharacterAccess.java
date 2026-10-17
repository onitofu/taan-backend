package taan.online.session.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.character.model.GameCharacter;
import taan.online.character.service.CharacterSelectionAccess;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;

public final class SessionCharacterAccess implements CharacterSelectionAccess {
    private final SessionRegistry sessions;

    public SessionCharacterAccess(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean trySelect(UUID sessionId, GameCharacter character) {
        return sessions.trySelect(new SessionId(sessionId), character);
    }

    @Override
    public Optional<CharacterId> selectedCharacter(UUID sessionId) {
        return sessions.selectedCharacter(new SessionId(sessionId));
    }

    @Override
    public Optional<AccountId> authenticatedAccount(UUID sessionId) {
        return sessions.selectablePrincipal(new SessionId(sessionId)).map(AuthenticatedPrincipal::accountId);
    }
}
