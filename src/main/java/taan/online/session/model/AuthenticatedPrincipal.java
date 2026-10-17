package taan.online.session.model;

import java.util.Objects;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;

public record AuthenticatedPrincipal(AccountId accountId, AccountGender gender) {
    public AuthenticatedPrincipal {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(gender);
    }
}
