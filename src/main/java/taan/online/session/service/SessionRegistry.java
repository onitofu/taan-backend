package taan.online.session.service;

import java.util.HashMap;
import java.util.Objects;
import taan.online.character.model.CharacterId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountId;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;
import taan.online.session.service.model.LoginIgnored;
import taan.online.session.service.model.LoginRejected;
import taan.online.session.service.model.LoginRejection;
import taan.online.session.service.model.LoginResult;
import taan.online.session.service.model.LoginSucceeded;

public final class SessionRegistry {
    private final Map<SessionId, PlayerSession> sessions = new HashMap<>();
    private final Map<AccountId, SessionId> claimsByAccount = new HashMap<>();

    public synchronized SessionId open() {
        var id = new SessionId(UUID.randomUUID());
        sessions.put(id, new PlayerSession(id));
        return id;
    }

    public synchronized SessionPhase phase(SessionId id) {
        var session = sessions.get(id);
        return session == null ? SessionPhase.CLOSED : session.phase;
    }

    public synchronized Optional<AuthenticatedPrincipal> principal(SessionId id) {
        var session = sessions.get(id);
        return session == null ? Optional.empty() : Optional.ofNullable(session.principal);
    }

    public synchronized boolean trySelect(SessionId id, CharacterId characterId) {
        Objects.requireNonNull(characterId);
        var session = sessions.get(id);

        if (session == null || session.phase != SessionPhase.AUTHENTICATED) {
            return false;
        }

        session.selectedCharacterId = characterId;
        session.phase = SessionPhase.CHARACTER_SELECTED;
        return true;
    }

    public synchronized Optional<CharacterId> selectedCharacter(SessionId id) {
        var session = sessions.get(id);
        return session == null ? Optional.empty() : Optional.ofNullable(session.selectedCharacterId);
    }

    public synchronized Optional<AuthenticatedPrincipal> selectablePrincipal(SessionId id) {
        var session = sessions.get(id);
        return session == null || session.phase != SessionPhase.AUTHENTICATED
            ? Optional.empty() : Optional.of(session.principal);
    }

    public synchronized LoginResult tryClaim(SessionId id, AuthenticatedPrincipal principal) {
        var session = sessions.get(id);

        if (session == null || session.phase != SessionPhase.ANONYMOUS) {
            return new LoginIgnored();
        }

        if (claimsByAccount.containsKey(principal.accountId())) {
            return new LoginRejected(LoginRejection.ACCOUNT_IN_USE);
        }

        claimsByAccount.put(principal.accountId(), id);
        session.principal = principal;
        session.phase = SessionPhase.AUTHENTICATED;
        return new LoginSucceeded(principal);
    }

    public synchronized Optional<AuthenticatedPrincipal> close(SessionId id) {
        var session = sessions.remove(id);

        if (session == null || session.principal == null) {
            return Optional.empty();
        }

        claimsByAccount.remove(session.principal.accountId(), id);
        return Optional.of(session.principal);
    }
}
