package taan.online.character.service;

import java.util.Optional;
import java.util.UUID;
import taan.online.account.model.AccountId;

public interface AuthenticatedAccountAccess {
    Optional<AccountId> authenticatedAccount(UUID sessionId);
}
