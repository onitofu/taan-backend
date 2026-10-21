package taan.online.character.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;

class CharacterBalancesJdbcTest {
    @Test
    void readsBalancesAndGoldThroughTheJdbcAdapter() {
        var stub = new CharacterJdbcStub();
        var repository = repository(stub);
        var balances = repository.findBalances(new AccountId(1), new CharacterId(3)).orElseThrow();

        assertEquals(3, balances.characterId().value());
        assertEquals(123, balances.cash());
        assertEquals(456, balances.gold());
        assertEquals(456, repository.findByAccount(new AccountId(1)).getFirst().gold());
        assertTrue(stub.calls.contains("connection.close"));

        stub.missingBalance = true;

        assertTrue(repository.findBalances(new AccountId(1), new CharacterId(3)).isEmpty());
    }

    @Test
    void sanitizesFailedQueriesAndCorruptStoredBalances() {
        for (String stage : List.of(
            "source.getConnection",
            "connection.prepareStatement",
            "statement.setQueryTimeout",
            "statement.setLong",
            "statement.executeQuery",
            "rows.next",
            "rows.getLong",
            "rows.close",
            "statement.close",
            "connection.close"
        )) {
            var stub = new CharacterJdbcStub();
            stub.failures.add(stage);
            var repository = repository(stub);
            var failure = assertThrows(
                CharacterPersistenceException.class,
                () -> repository.findBalances(new AccountId(1), new CharacterId(3))
            );

            assertFalse(failure.getMessage().contains("private-database-value"));
            assertFalse(failure.getCause().getMessage().contains("private-database-value"));
        }

        var corrupt = new CharacterJdbcStub();
        corrupt.gold = -1;
        var repository = repository(corrupt);

        assertThrows(
            CharacterPersistenceException.class,
            () -> repository.findBalances(
                new AccountId(1),
                new CharacterId(3)
            )
        );
    }

    private JdbcCharacterRepository repository(CharacterJdbcStub stub) {
        return new JdbcCharacterRepository(stub.source(), 5, new JdbcInventoryRepository(stub.source(), 5));
    }
}
