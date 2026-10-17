package taan.online.character.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.reason;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.account.model.AccountId;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.model.CharacterId;
import taan.online.character.repository.CharacterRepository;
import taan.online.character.repository.CharacterTransaction;
import taan.online.character.service.model.DeleteCharacterOutcome;
import taan.online.character.service.model.DeleteCharacterResult;
import taan.online.logging.EventLogger;

public final class DeleteCharacterAction {
    private static final EventLogger LOG = EventLogger.forClass(DeleteCharacterAction.class);

    private final AuthenticatedAccountAccess sessions;
    private final CharacterRepository characters;

    public DeleteCharacterAction(AuthenticatedAccountAccess sessions, CharacterRepository characters) {
        this.sessions = sessions;
        this.characters = characters;
    }

    public DeleteCharacterResult execute(UUID sessionId, long requestedId) {
        long started = System.nanoTime();
        var owner = sessions.authenticatedAccount(sessionId);

        if (owner.isEmpty()) {
            return new DeleteCharacterResult(DeleteCharacterOutcome.SESSION_CLOSED, List.of());
        }

        var account = owner.orElseThrow();

        if (requestedId < 1 || requestedId > 0xffff_ffffL) {
            return rejected(account, started);
        }

        DeleteCharacterResult result;

        try (var transaction = characters.beginTransaction(account)) {
            result = deleteInTransaction(transaction, account, new CharacterId(requestedId), started);
        }

        return result;
    }

    private DeleteCharacterResult deleteInTransaction(
        CharacterTransaction transaction,
        AccountId account,
        CharacterId target,
        long started
    ) {
        if (!transaction.archive(target)) {
            return rejected(account, started);
        }

        var result = new DeleteCharacterResult(DeleteCharacterOutcome.DELETED, transaction.catalog());
        transaction.commit();

        LOG.log(
            CharacterLogEvent.DELETION_SUCCEEDED,
            accountId(account.value()),
            characterId(target.value()),
            outcome("DELETED"),
            durationMs(elapsed(started))
        );
        return result;
    }

    private DeleteCharacterResult rejected(AccountId account, long started) {
        LOG.log(
            CharacterLogEvent.DELETION_REJECTED,
            accountId(account.value()),
            reason("NOT_FOUND"),
            durationMs(elapsed(started))
        );
        return new DeleteCharacterResult(DeleteCharacterOutcome.NOT_FOUND, List.of());
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
