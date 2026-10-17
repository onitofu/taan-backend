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
import taan.online.character.model.CharacterCreationOptions;
import taan.online.character.model.CharacterName;
import taan.online.character.model.CharacterNameFilter;
import taan.online.character.repository.CharacterRepository;
import taan.online.character.repository.CharacterTransaction;
import taan.online.character.service.model.CreateCharacterCommand;
import taan.online.character.service.model.CreateCharacterOutcome;
import taan.online.character.service.model.CreateCharacterResult;
import taan.online.inventory.model.StarterEquipment;
import taan.online.inventory.model.StarterItem;
import taan.online.logging.EventLogger;

public final class CreateCharacterAction {
    private static final EventLogger LOG = EventLogger.forClass(CreateCharacterAction.class);

    private final AuthenticatedAccountAccess sessions;
    private final CharacterRepository characters;
    private final CharacterNameFilter filter;
    private final CharacterCreationOptions options;

    public CreateCharacterAction(
        AuthenticatedAccountAccess sessions,
        CharacterRepository characters,
        CharacterNameFilter filter,
        CharacterCreationOptions options
    ) {
        this.sessions = sessions;
        this.characters = characters;
        this.filter = filter;
        this.options = options;
    }

    public CreateCharacterResult execute(UUID sessionId, CreateCharacterCommand command) {
        long started = System.nanoTime();
        var owner = sessions.authenticatedAccount(sessionId);

        if (owner.isEmpty()) {
            return new CreateCharacterResult(CreateCharacterOutcome.SESSION_CLOSED, List.of());
        }

        var account = owner.orElseThrow();
        CharacterName name;

        try {
            name = new CharacterName(command.name());
        } catch (IllegalArgumentException failure) {
            return rejected(CreateCharacterOutcome.INVALID_NAME, account, started);
        }

        if (filter.forbids(name)) {
            return rejected(CreateCharacterOutcome.INVALID_NAME, account, started);
        }

        CreateCharacterResult result;

        try (var transaction = characters.beginTransaction(account)) {
            result = createInTransaction(transaction, account, name, command, started);
        }

        return result;
    }

    private CreateCharacterResult createInTransaction(
        CharacterTransaction transaction,
        AccountId account,
        CharacterName name,
        CreateCharacterCommand command,
        long started
    ) {
        if (transaction.hasCharacters()) {
            return rejected(CreateCharacterOutcome.ACCOUNT_HAS_CHARACTERS, account, started);
        }

        if (!options.allows(transaction.accountGender(), command.appearance())) {
            return rejected(CreateCharacterOutcome.INVALID_APPEARANCE, account, started);
        }

        var starter = new StarterEquipment(
            command.appearance().parts().stream().filter(part -> part.itemId() != 0)
                .map(
                    part -> new StarterItem(
                        part.slot(),
                        part.itemId(),
                        part.parameter0(),
                        part.parameter1(),
                        part.parameter2()
                    )
                ).toList()
        );
        var created = transaction.tryInsert(name, starter);

        if (created.isEmpty()) {
            return rejected(CreateCharacterOutcome.NAME_TAKEN, account, started);
        }

        var result = new CreateCharacterResult(CreateCharacterOutcome.CREATED, transaction.catalog());
        transaction.commit();

        LOG.log(
            CharacterLogEvent.CREATION_SUCCEEDED,
            accountId(account.value()),
            characterId(created.orElseThrow().value()),
            outcome("CREATED"),
            durationMs(elapsed(started))
        );
        return result;
    }

    private CreateCharacterResult rejected(CreateCharacterOutcome rejection, AccountId account, long started) {
        LOG.log(
            CharacterLogEvent.CREATION_REJECTED,
            accountId(account.value()),
            reason(rejection.name()),
            durationMs(elapsed(started))
        );
        return new CreateCharacterResult(rejection, List.of());
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
