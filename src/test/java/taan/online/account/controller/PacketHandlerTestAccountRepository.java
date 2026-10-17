package taan.online.account.controller;

import java.time.Instant;
import java.util.Optional;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

final class PacketHandlerTestAccountRepository implements AccountRepository {
    private Account saved;
    private boolean fail;

    @Override
    public boolean existsByLogin(LoginName login) {
        if (fail) {
            throw new IllegalStateException("Persistence unavailable");
        }

        return saved != null;
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        saved = new Account(new AccountId(1), login, verifier, gender, Instant.EPOCH);
        return Optional.of(saved);
    }

    Account saved() {
        return saved;
    }

    void fail(boolean fail) {
        this.fail = fail;
    }
}
