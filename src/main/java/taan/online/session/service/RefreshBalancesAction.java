package taan.online.session.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import taan.online.character.model.CharacterBalanceSnapshot;
import taan.online.character.model.CharacterId;
import taan.online.character.repository.CharacterRepository;
import taan.online.logging.EventLogger;
import taan.online.session.logging.SessionLogEvent;
import taan.online.session.model.SessionId;

public final class RefreshBalancesAction {
    private static final EventLogger LOG = EventLogger.forClass(RefreshBalancesAction.class);

    private final SessionRegistry sessions;
    private final CharacterRepository characters;

    public RefreshBalancesAction(SessionRegistry sessions, CharacterRepository characters) {
        this.sessions = sessions;
        this.characters = characters;
    }

    public Optional<CharacterBalanceSnapshot> execute(UUID requestedSession) {
        long started = System.nanoTime();
        var id = new SessionId(requestedSession);
        var selected = sessions.inventoryActor(id);

        if (selected.isEmpty()) {
            return ignored(id);
        }

        var owner = selected.orElseThrow().accountId();
        var character = new CharacterId(selected.orElseThrow().characterId());
        var balances = characters.findBalances(owner, character);

        if (balances.isEmpty() || !sessions.inventoryActor(id).equals(selected)) {
            return ignored(id);
        }

        LOG.log(
            SessionLogEvent.BALANCES_REFRESHED,
            sessionId(requestedSession),
            accountId(owner.value()),
            characterId(character.value()),
            durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        );
        return balances;
    }

    private Optional<CharacterBalanceSnapshot> ignored(SessionId id) {
        LOG.log(SessionLogEvent.BALANCES_REFRESH_IGNORED, sessionId(id.value()), reason("CONTEXT_UNAVAILABLE"));
        return Optional.empty();
    }
}
