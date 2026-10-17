package taan.online.server;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

final class BlockingAccountTestRepository implements AccountRepository {
    private final AtomicInteger checks = new AtomicInteger();
    private final AtomicInteger inserts = new AtomicInteger();
    private Runnable onCheck = () -> { };

    @Override
    public Optional<Account> findByLogin(LoginName login) {
        return Optional.empty();
    }

    @Override
    public boolean existsByLogin(LoginName login) {
        checks.incrementAndGet();
        onCheck.run();
        return false;
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        inserts.incrementAndGet();
        return Optional.of(new Account(new AccountId(1), login, verifier, gender, Instant.EPOCH));
    }

    int checks() {
        return checks.get();
    }

    int inserts() {
        return inserts.get();
    }

    void onCheck(Runnable onCheck) {
        this.onCheck = onCheck;
    }
}
