package taan.online.session.service;

import java.time.Instant;
import java.util.Optional;
import taan.online.TestFixtures;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

public final class LoginTestAccountRepository implements AccountRepository {
    public Account account = new Account(
        new AccountId(1),
        new LoginName(TestFixtures.LOGIN),
        new CredentialVerifier(TestFixtures.VERIFIER),
        AccountGender.FEMALE,
        Instant.EPOCH
    );
    public Runnable onRead = () -> { };
    public int reads;

    @Override
    public Optional<Account> findByLogin(LoginName login) {
        reads++;
        onRead.run();
        return Optional.ofNullable(account);
    }

    @Override
    public boolean existsByLogin(LoginName login) {
        return account != null;
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        throw new AssertionError("Login must not insert accounts");
    }
}
