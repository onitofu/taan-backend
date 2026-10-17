package taan.online.account.service;

import java.time.Instant;
import java.util.Optional;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

final class ActionTestAccountRepository implements AccountRepository {
    private Account saved;
    private boolean conflict;
    private int inserts;

    @Override
    public Optional<Account> findByLogin(LoginName login) {
        return Optional.ofNullable(saved);
    }

    @Override
    public boolean existsByLogin(LoginName login) {
        return saved != null;
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        inserts++;

        if (conflict) {
            return Optional.empty();
        }

        saved = new Account(new AccountId(1), login, verifier, gender, Instant.EPOCH);
        return Optional.of(saved);
    }

    Account saved() {
        return saved;
    }

    int inserts() {
        return inserts;
    }

    void conflict(boolean conflict) {
        this.conflict = conflict;
    }
}
