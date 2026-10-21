package taan.online.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.session.model.AuthenticatedPrincipal;
import taan.online.session.model.SessionId;

class RefreshBalancesActionTest {
    @Test
    void readsBothBalancesAgainAndNeverTrustsAClientBalance() {
        var sessions = new SessionRegistry();
        var id = selected(sessions);
        var characters = new TestCharacterRepository();
        var original = CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "PrivateHero");
        characters.stored.add(withGold(original, 9876));
        characters.cash = 4321;
        var action = new RefreshBalancesAction(sessions, characters);

        try (var logs = new LogCapture()) {
            var first = action.execute(id.value()).orElseThrow();

            assertEquals(4321, first.cash());
            assertEquals(9876, first.gold());
            assertEquals(1001, first.characterId().value());

            characters.cash = 0xffff_ffffL;
            characters.stored.set(0, withGold(original, 0));
            var second = action.execute(id.value()).orElseThrow();

            assertEquals(0xffff_ffffL, second.cash());
            assertEquals(0, second.gold());
            assertEquals(2, characters.reads);
            assertTrue(logs.text().contains("session.balances.refreshed"));
            assertFalse(logs.text().contains("PrivateHero"));
            assertFalse(logs.text().contains("gold="));
            assertFalse(logs.text().contains("cash="));
        }
    }

    @Test
    void ignoresMissingUnselectedForeignArchivedAndDisconnectedContexts() {
        var sessions = new SessionRegistry();
        var repository = new TestCharacterRepository();
        var action = new RefreshBalancesAction(sessions, repository);
        var anonymous = sessions.open();

        assertTrue(action.execute(anonymous.value()).isEmpty());
        sessions.tryClaim(anonymous, new AuthenticatedPrincipal(new AccountId(2), AccountGender.FEMALE));
        assertTrue(action.execute(anonymous.value()).isEmpty());
        assertEquals(0, repository.reads);

        var id = selected(sessions);

        assertTrue(action.execute(id.value()).isEmpty());
        repository.stored.add(CharacterFixtures.character(1001, new AccountId(2), AccountGender.MALE, "Foreign"));
        assertTrue(action.execute(id.value()).isEmpty());
        repository.stored.clear();
        repository.archived.add(CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "Archived"));
        assertTrue(action.execute(id.value()).isEmpty());

        repository.stored.add(CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "Owner"));
        repository.onRead = () -> sessions.close(id);

        assertTrue(action.execute(id.value()).isEmpty());
        assertTrue(action.execute(id.value()).isEmpty());
    }

    @Test
    void propagatesDatabaseFailureWithoutInventingZeroBalances() {
        var sessions = new SessionRegistry();
        var id = selected(sessions);
        var repository = new TestCharacterRepository();
        repository.failure = new IllegalStateException("private-database-value");
        var action = new RefreshBalancesAction(sessions, repository);

        assertEquals(repository.failure, assertThrows(IllegalStateException.class, () -> action.execute(id.value())));
    }

    private SessionId selected(SessionRegistry sessions) {
        var id = sessions.open();
        sessions.tryClaim(id, new AuthenticatedPrincipal(new AccountId(1), AccountGender.MALE));
        sessions.trySelect(id, CharacterFixtures.character(1001, new AccountId(1), AccountGender.MALE, "Owner"));
        return id;
    }

    private GameCharacter withGold(GameCharacter original, long gold) {
        return new GameCharacter(
            original.id(),
            original.accountId(),
            original.name(),
            original.level(),
            original.gender(),
            original.appearance(),
            original.createdAt(),
            original.progress(),
            gold
        );
    }
}
