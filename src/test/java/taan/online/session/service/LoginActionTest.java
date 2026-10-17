package taan.online.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.TestCredentialHasher;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.LoginName;
import taan.online.account.service.model.ClientCredential;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;
import taan.online.session.service.model.LoginCommand;
import taan.online.session.service.model.LoginIgnored;
import taan.online.session.service.model.LoginRejected;
import taan.online.session.service.model.LoginRejection;
import taan.online.session.service.model.LoginSucceeded;

class LoginActionTest {
    @Test
    void dropsVerifiedIdentityWhenDisconnectedDuringCredentialVerification() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var id = sessions.open();
        var hasher = new TestCredentialHasher(
            credential -> repository.account.credentialVerifier(),
            (credential, verifier) -> {
                sessions.close(id);
                return true;
            }
        );
        var action = new LoginAction(repository, hasher, sessions);

        assertInstanceOf(LoginIgnored.class, action.execute(id, command(TestFixtures.CREDENTIAL)));
        assertEquals(SessionPhase.CLOSED, sessions.phase(id));
        assertInstanceOf(LoginSucceeded.class, action.execute(sessions.open(), command(TestFixtures.CREDENTIAL)));
    }

    @Test
    void authenticatesCanonicalLoginAndIgnoresFurtherAttempts() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var id = sessions.open();
        var action = action(repository, sessions);
        var success = assertInstanceOf(LoginSucceeded.class, action.execute(id, command(TestFixtures.CREDENTIAL)));

        assertEquals(repository.account.id(), success.principal().accountId());
        assertEquals(SessionPhase.AUTHENTICATED, sessions.phase(id));
        assertInstanceOf(LoginIgnored.class, action.execute(id, command("0".repeat(32))));
        assertEquals(1, repository.reads);

        sessions.close(id);

        assertEquals(SessionPhase.CLOSED, sessions.phase(id));
        assertInstanceOf(LoginIgnored.class, action.execute(id, command(TestFixtures.CREDENTIAL)));
        assertEquals(1, repository.reads);
    }

    @Test
    void distinguishesExpectedFailuresWithoutCreatingClaims() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var action = action(repository, sessions);
        var id = sessions.open();

        assertEquals(
            new LoginRejected(LoginRejection.WRONG_CREDENTIAL),
            action.execute(id, command("0".repeat(32)))
        );
        assertEquals(SessionPhase.ANONYMOUS, sessions.phase(id));

        repository.account = null;

        assertEquals(
            new LoginRejected(LoginRejection.ACCOUNT_NOT_FOUND),
            action.execute(id, command(TestFixtures.CREDENTIAL))
        );
        repository.onRead = () -> {
            throw new IllegalStateException("secret");
        };

        assertThrows(IllegalStateException.class, () -> action.execute(id, command(TestFixtures.CREDENTIAL)));
    }

    @Test
    void neverRevivesSessionClosedDuringVerification() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var id = sessions.open();
        repository.onRead = () -> sessions.close(id);

        assertInstanceOf(
            LoginIgnored.class,
            action(
                repository,
                sessions
            ).execute(
                id,
                command(
                    TestFixtures.CREDENTIAL
                )
            )
        );
        assertEquals(SessionPhase.CLOSED, sessions.phase(id));
        assertTrue(sessions.close(id).isEmpty());
    }

    @Test
    void claimsAccountAtomicallyAcrossConcurrentSessions() throws Exception {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var first = sessions.open();
        var second = sessions.open();
        var principal = new AuthenticatedPrincipal(repository.account.id(), repository.account.gender());
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return sessions.tryClaim(first, principal);
                }
            );
            var right = workers.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return sessions.tryClaim(second, principal);
                }
            );
            var a = left.get(5, TimeUnit.SECONDS);
            var b = right.get(5, TimeUnit.SECONDS);

            assertEquals(1, (a instanceof LoginSucceeded ? 1 : 0) + (b instanceof LoginSucceeded ? 1 : 0));
            assertEquals(1, (a instanceof LoginRejected ? 1 : 0) + (b instanceof LoginRejected ? 1 : 0));
        }
    }

    @Test
    void lateCleanupCannotReleaseNewSessionAndIndependentAccountsCoexist() {
        var sessions = new SessionRegistry();
        var principal = new AuthenticatedPrincipal(new AccountId(1), AccountGender.MALE);
        var old = sessions.open();

        assertInstanceOf(LoginSucceeded.class, sessions.tryClaim(old, principal));
        assertInstanceOf(LoginIgnored.class, sessions.tryClaim(old, principal));
        assertTrue(sessions.close(old).isPresent());

        var fresh = sessions.open();

        assertInstanceOf(LoginSucceeded.class, sessions.tryClaim(fresh, principal));
        assertTrue(sessions.close(old).isEmpty());
        assertInstanceOf(LoginRejected.class, sessions.tryClaim(sessions.open(), principal));
        assertInstanceOf(
            LoginSucceeded.class,
            sessions.tryClaim(
                sessions.open(),
                new AuthenticatedPrincipal(new AccountId(2), AccountGender.FEMALE)
            )
        );
        assertInstanceOf(LoginIgnored.class, sessions.tryClaim(new SessionId(UUID.randomUUID()), principal));

        var anonymous = sessions.open();

        assertTrue(sessions.close(anonymous).isEmpty());
        assertEquals(SessionPhase.CLOSED, sessions.phase(anonymous));
        assertEquals(SessionPhase.AUTHENTICATED, sessions.phase(fresh));
    }

    private static LoginAction action(LoginTestAccountRepository repository, SessionRegistry sessions) {
        return new LoginAction(
            repository,
            new TestCredentialHasher(
                credential -> repository.account.credentialVerifier()
            ),
            sessions
        );
    }

    private static LoginCommand command(String credential) {
        return new LoginCommand(new LoginName(TestFixtures.LOGIN.toUpperCase()), new ClientCredential(credential));
    }
}
