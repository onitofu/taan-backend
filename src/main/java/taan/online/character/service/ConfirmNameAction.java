package taan.online.character.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.outcome;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.model.CharacterName;
import taan.online.character.model.CharacterNameFilter;
import taan.online.character.repository.CharacterRepository;
import taan.online.character.service.model.ConfirmNameResult;
import taan.online.logging.EventLogger;

public final class ConfirmNameAction {
    private static final EventLogger LOG = EventLogger.forClass(ConfirmNameAction.class);

    private final AuthenticatedAccountAccess sessions;
    private final CharacterRepository characters;
    private final CharacterNameFilter filter;

    public ConfirmNameAction(
        AuthenticatedAccountAccess sessions,
        CharacterRepository characters,
        CharacterNameFilter filter
    ) {
        this.sessions = sessions;
        this.characters = characters;
        this.filter = filter;
    }

    public ConfirmNameResult execute(UUID sessionId, String value) {
        long started = System.nanoTime();
        var owner = sessions.authenticatedAccount(sessionId);

        if (owner.isEmpty()) {
            return ConfirmNameResult.SESSION_CLOSED;
        }

        CharacterName name;
        ConfirmNameResult result;

        try {
            name = new CharacterName(value);
            result = filter.forbids(name) ? ConfirmNameResult.INVALID_NAME : check(name);
        } catch (IllegalArgumentException failure) {
            result = ConfirmNameResult.INVALID_NAME;
        }

        LOG.log(
            CharacterLogEvent.NAME_CHECKED,
            accountId(owner.orElseThrow().value()),
            outcome(result.name()),
            durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        );
        return result;
    }

    private ConfirmNameResult check(CharacterName name) {
        return characters.existsByName(name) ? ConfirmNameResult.NAME_TAKEN : ConfirmNameResult.AVAILABLE;
    }
}
