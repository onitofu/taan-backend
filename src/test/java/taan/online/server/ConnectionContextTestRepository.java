package taan.online.server;

import java.time.Instant;
import java.util.Optional;
import taan.online.TestFixtures;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

final class ConnectionContextTestRepository implements AccountRepository {
    private boolean exists;
    private RuntimeException failure;
    private int checks;
    private int inserts;
    private Runnable onCheck = () -> { };

    @Override
    public Optional<Account> findByLogin(LoginName login) {
        onCheck.run();

        if (failure != null) {
            throw failure;
        }

        return exists ? Optional.of(
            new Account(
                new AccountId(1),
                login,
                new CredentialVerifier(TestFixtures.VERIFIER),
                AccountGender.FEMALE,
                Instant.EPOCH
            )
        ) : Optional.empty();
    }

    @Override
    public boolean existsByLogin(LoginName login) {
        checks++;
        onCheck.run();

        if (failure != null) {
            throw failure;
        }

        return exists;
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        inserts++;
        exists = true;
        return Optional.of(new Account(new AccountId(1), login, verifier, gender, Instant.EPOCH));
    }

    int checks() {
        return checks;
    }

    int inserts() {
        return inserts;
    }

    void exists(boolean exists) {
        this.exists = exists;
    }

    void failure(RuntimeException failure) {
        this.failure = failure;
    }

    void onCheck(Runnable onCheck) {
        this.onCheck = onCheck;
    }
}
