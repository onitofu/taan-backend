package taan.online.character.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.model.CharacterId;
import taan.online.character.repository.CharacterRepository;
import taan.online.character.service.model.SelectCharacterOutcome;
import taan.online.character.service.model.SelectCharacterResult;
import taan.online.logging.EventLogger;

public final class SelectCharacterAction {
    private static final EventLogger LOG = EventLogger.forClass(SelectCharacterAction.class);

    private final CharacterSelectionAccess sessions;
    private final CharacterRepository characters;

    public SelectCharacterAction(CharacterSelectionAccess sessions, CharacterRepository characters) {
        this.sessions = sessions;
        this.characters = characters;
    }

    public SelectCharacterResult execute(UUID sessionId, long requestedId) {
        long started = System.nanoTime();
        var owner = sessions.authenticatedAccount(sessionId);

        if (owner.isEmpty()) {
            return ignored();
        }

        SelectCharacterResult result;

        try (var transaction = characters.beginTransaction(owner.orElseThrow())) {
            var selected = transaction.catalog().stream()
                .filter(value -> value.id().value() == requestedId).findFirst();

            if (selected.isPresent()) {
                var character = selected.orElseThrow();
                var inventories = transaction.inventories(character.id());
                result = new SelectCharacterResult(SelectCharacterOutcome.SELECTED, character, inventories);
                transaction.commit();
            } else {
                result = new SelectCharacterResult(SelectCharacterOutcome.REJECTED, null, List.of());
            }
        }

        if (result.outcome() == SelectCharacterOutcome.REJECTED) {
            LOG.log(
                CharacterLogEvent.SELECTION_REJECTED,
                accountId(owner.orElseThrow().value()),
                reason("INVALID_CHARACTER"),
                durationMs(elapsed(started))
            );
            return result;
        }

        CharacterId selectedId = result.character().id();

        if (!sessions.trySelect(sessionId, selectedId)) {
            return ignored();
        }

        LOG.log(
            CharacterLogEvent.SELECTION_SUCCEEDED,
            accountId(owner.orElseThrow().value()),
            characterId(selectedId.value()),
            durationMs(elapsed(started))
        );
        return result;
    }

    private SelectCharacterResult ignored() {
        LOG.log(CharacterLogEvent.SELECTION_IGNORED, reason("SESSION_NOT_SELECTABLE"));
        return new SelectCharacterResult(SelectCharacterOutcome.IGNORED, null, List.of());
    }

    private long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
