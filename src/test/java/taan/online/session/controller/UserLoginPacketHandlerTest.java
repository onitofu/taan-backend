package taan.online.session.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestCredentialHasher;
import taan.online.TestFixtures;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.service.model.ClientCredential;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;
import taan.online.session.model.SessionPhase;
import taan.online.session.service.LoginAction;
import taan.online.session.service.LoginTestAccountRepository;
import taan.online.session.service.SessionRegistry;
import taan.online.session.service.model.LoginCommand;

class UserLoginPacketHandlerTest {
    @Test
    void ignoresRequestsAfterSessionClosureWithoutDecodingOrAccessingCredentials() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var verifications = new AtomicInteger();
        var handler = handler(repository, sessions, verifications);
        var id = new SessionId(handler.open(UUID.randomUUID()));
        byte[] request = TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL);
        handler.close("TEST");

        try (var logs = new LogCapture()) {
            assertTrue(handler.handle(request).isEmpty());
            assertTrue(handler.handle(Arrays.copyOf(request, 4)).isEmpty());

            assertEquals(0, repository.reads);
            assertEquals(0, verifications.get());
            assertEquals(SessionPhase.CLOSED, sessions.phase(id));
            assertIgnoredLogs(logs);
        }
    }

    @Test
    void ignoresRequestsAfterAuthenticationWithoutChangingIdentityOrCheckingCredentialsAgain() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var verifications = new AtomicInteger();
        var handler = handler(repository, sessions, verifications);
        var id = new SessionId(handler.open(UUID.randomUUID()));
        String otherLogin = "other@example.test";
        String wrongCredential = "0".repeat(32);

        try (var logs = new LogCapture()) {
            var reply = handler.handle(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL)).orElseThrow();

            assertTrue(reply.success());
            assertEquals(repository.account.id().value(), reply.accountId());
            assertEquals(1, repository.reads);
            assertEquals(1, verifications.get());

            byte[] repeated = TestFixtures.login(otherLogin, wrongCredential);
            assertTrue(handler.handle(repeated).isEmpty());
            assertTrue(handler.handle(Arrays.copyOf(repeated, 4)).isEmpty());

            assertEquals(1, repository.reads);
            assertEquals(1, verifications.get());
            assertEquals(SessionPhase.AUTHENTICATED, sessions.phase(id));
            assertIgnoredLogs(logs);
            assertFalse(logs.text().contains(otherLogin));
            assertFalse(logs.text().contains(wrongCredential));
        }

        assertEquals(
            new AuthenticatedPrincipal(repository.account.id(), repository.account.gender()),
            sessions.close(id).orElseThrow()
        );
    }

    @Test
    void rejectsUnsupportedLocalAccountCountsAndInvalidNamesBeforeDatabaseRead() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var handler = handler(repository, sessions);
        handler.open(UUID.randomUUID());
        byte[] empty = ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x198b0459).putInt(0).putInt(0).putShort((short) 0).put((byte) 0).array();

        assertEquals(0, handler.handle(empty).orElseThrow().errorCode());
        assertEquals(
            0,
            handler.handle(TestFixtures.fixture("login-two-local-accounts.root.hex"))
            .orElseThrow().errorCode()
        );
        assertEquals(
            0,
            handler.handle(TestFixtures.login("invalid name", TestFixtures.CREDENTIAL))
            .orElseThrow().errorCode()
        );
        assertEquals(0, repository.reads);

        handler.close("TEST");
    }

    @Test
    void mapsBothGendersAndRedactsApplicationCommands() {
        for (var gender : AccountGender.values()) {
            var repository = new LoginTestAccountRepository();
            var account = repository.account;
            repository.account = new Account(
                account.id(),
                account.login(),
                account.credentialVerifier(),
                gender,
                account.createdAt()
            );
            var handler = handler(repository, new SessionRegistry());
            handler.open(UUID.randomUUID());
            var reply = handler.handle(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL)).orElseThrow();

            assertTrue(reply.success());
            assertEquals(gender == AccountGender.MALE ? 1 : 2, reply.gender());
            assertFalse(
                new LoginCommand(
                    account.login(),
                    new ClientCredential(TestFixtures.CREDENTIAL)
                )
                .toString().contains(TestFixtures.CREDENTIAL)
            );

            handler.close("TEST");
        }
    }

    @Test
    void ignoresResultWhenAnotherAttemptAlreadyAuthenticatedTheSameSession() {
        var repository = new LoginTestAccountRepository();
        var sessions = new SessionRegistry();
        var handler = handler(repository, sessions);
        var id = new SessionId(handler.open(UUID.randomUUID()));
        repository.onRead = () -> sessions.tryClaim(
            id,
            new AuthenticatedPrincipal(repository.account.id(), repository.account.gender())
        );

        assertTrue(handler.handle(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL)).isEmpty());

        handler.close("TEST");
    }

    private static UserLoginPacketHandler handler(LoginTestAccountRepository repository, SessionRegistry sessions) {
        var hasher = new TestCredentialHasher(credential -> repository.account.credentialVerifier());
        return new UserLoginPacketHandler(new LoginAction(repository, hasher, sessions), sessions);
    }

    private static UserLoginPacketHandler handler(
        LoginTestAccountRepository repository,
        SessionRegistry sessions,
        AtomicInteger verifications
    ) {
        var hasher = new TestCredentialHasher(
            credential -> {
                throw new AssertionError("Login must not hash credentials");
            },
            (credential, verifier) -> {
                verifications.incrementAndGet();
                return credential.hex().equals(TestFixtures.CREDENTIAL);
            }
        );
        return new UserLoginPacketHandler(new LoginAction(repository, hasher, sessions), sessions);
    }

    private static void assertIgnoredLogs(LogCapture logs) {
        var ignored = logs.events().stream().filter(
            event -> event.getKeyValuePairs().stream().anyMatch(
                field -> field.key.equals("event") && field.value.equals("account.login.ignored")
            )
        ).toList();

        assertEquals(2, ignored.size());

        for (var event : ignored) {
            assertEquals(Level.DEBUG, event.getLevel());
            boolean expectedReason = event.getKeyValuePairs().stream().anyMatch(
                field -> field.key.equals("reason") && field.value.equals("SESSION_NOT_ANONYMOUS")
            );

            assertTrue(expectedReason);
        }

        assertFalse(logs.text().contains(TestFixtures.LOGIN));
        assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
        assertFalse(logs.text().contains(TestFixtures.VERIFIER));
    }
}
