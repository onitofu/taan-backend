package taan.online.session.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountId;
import taan.online.character.service.AuthenticatedAccountAccess;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;

public final class SessionCharacterAccess implements AuthenticatedAccountAccess {
    private final SessionRegistry sessions;

    public SessionCharacterAccess(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<AccountId> authenticatedAccount(UUID sessionId) {
        return sessions.principal(new SessionId(sessionId)).map(AuthenticatedPrincipal::accountId);
    }
}
