package taan.online.session.service;

import taan.online.account.repository.AccountRepository;
import taan.online.account.service.CredentialHasher;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;
import taan.online.session.service.model.LoginCommand;
import taan.online.session.service.model.LoginIgnored;
import taan.online.session.service.model.LoginRejected;
import taan.online.session.service.model.LoginRejection;
import taan.online.session.service.model.LoginResult;

public final class LoginAction {
    private final AccountRepository accounts;
    private final CredentialHasher credentials;
    private final SessionRegistry sessions;

    public LoginAction(AccountRepository accounts, CredentialHasher credentials, SessionRegistry sessions) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.sessions = sessions;
    }

    public LoginResult execute(SessionId sessionId, LoginCommand command) {
        if (sessions.phase(sessionId) != SessionPhase.ANONYMOUS) {
            return new LoginIgnored();
        }

        var account = accounts.findByLogin(command.login());

        if (account.isEmpty()) {
            return new LoginRejected(LoginRejection.ACCOUNT_NOT_FOUND);
        }

        var stored = account.orElseThrow();

        if (!credentials.verify(command.credential(), stored.credentialVerifier())) {
            return new LoginRejected(LoginRejection.WRONG_CREDENTIAL);
        }

        return sessions.tryClaim(sessionId, new AuthenticatedPrincipal(stored.id(), stored.gender()));
    }
}
