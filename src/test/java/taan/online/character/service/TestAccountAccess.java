package taan.online.character.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountId;

public final class TestAccountAccess implements AuthenticatedAccountAccess {
    public boolean active = true;
    public int calls;
    public Runnable onAccess = () -> { };
    public final AccountId owner = new AccountId(1);

    @Override
    public Optional<AccountId> authenticatedAccount(UUID sessionId) {
        calls++;
        onAccess.run();
        return active ? Optional.of(owner) : Optional.empty();
    }
}
