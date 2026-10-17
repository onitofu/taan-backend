package taan.online.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.service.model.CheckAccountResult;
import taan.online.account.service.model.ClientCredential;
import taan.online.account.service.model.RegisterAccountCommand;
import taan.online.account.service.model.RegistrationResult;

class AccountActionTest {
    @Test
    void checksAccountsAndRegistersOnlyAfterHashing() {
        var repository = new ActionTestAccountRepository();
        var check = new CheckAccountAction(repository);

        assertEquals(CheckAccountResult.NOT_FOUND, check.execute(new LoginName(TestFixtures.LOGIN)));

        var hashes = new AtomicInteger();
        var action = new RegisterAccountAction(
            repository,
            credential -> {
                hashes.incrementAndGet();
                return new CredentialVerifier(TestFixtures.VERIFIER);
            }
        );

        assertEquals(RegistrationResult.CREATED, action.execute(command()));
        assertEquals(TestFixtures.VERIFIER, repository.saved().credentialVerifier().encoded());
        assertEquals(CheckAccountResult.EXISTS, check.execute(command().login()));
        assertEquals(RegistrationResult.ALREADY_EXISTS, action.execute(command()));
        assertEquals(1, repository.inserts());
        assertEquals(1, hashes.get());
    }

    @Test
    void handlesConcurrentUniqueConflictAndPropagatesInfrastructureFailures() {
        var repository = new ActionTestAccountRepository();
        repository.conflict(true);
        var action = new RegisterAccountAction(repository, credential -> new CredentialVerifier(TestFixtures.VERIFIER));

        assertEquals(RegistrationResult.ALREADY_EXISTS, action.execute(command()));

        var failed = new RegisterAccountAction(
            repository,
            credential -> {
                throw new IllegalStateException("Hashing unavailable");
            }
        );

        assertThrows(IllegalStateException.class, () -> failed.execute(command()));
        assertEquals(1, repository.inserts());
    }

    static RegisterAccountCommand command() {
        return new RegisterAccountCommand(
            new LoginName(TestFixtures.LOGIN),
            new ClientCredential(TestFixtures.CREDENTIAL),
            AccountGender.FEMALE
        );
    }
}
