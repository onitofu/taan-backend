package taan.online.account.service;

import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;
import taan.online.account.service.model.CheckAccountResult;

public final class CheckAccountAction {
    private final AccountRepository accounts;

    public CheckAccountAction(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public CheckAccountResult execute(LoginName login) {
        return accounts.existsByLogin(login) ? CheckAccountResult.EXISTS : CheckAccountResult.NOT_FOUND;
    }
}
