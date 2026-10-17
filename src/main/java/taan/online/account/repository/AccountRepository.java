package taan.online.account.repository;

import java.util.Optional;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;

public interface AccountRepository {
    boolean existsByLogin(LoginName login);

    Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender);
}
