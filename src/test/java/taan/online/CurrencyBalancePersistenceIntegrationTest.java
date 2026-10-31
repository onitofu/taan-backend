package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.account.model.AccountId;
import taan.online.character.model.CharacterId;

class CurrencyBalancePersistenceIntegrationTest {
    private static CharacterTestDatabase database;

    @BeforeAll
    static void start() {
        database = new CharacterTestDatabase();
    }

    @AfterAll
    static void stop() {
        if (database != null) {
            database.close();
        }
    }

    @BeforeEach
    void reset() throws SQLException {
        database.reset();
        seed(database.source());
    }

    @Test
    void preservesBalancesAndProgressAcrossRepeatedMigration() throws SQLException {
        database.execute("CREATE SCHEMA currency_upgrade");
        var source = freshSource();
        source.setCurrentSchema("currency_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("currency_upgrade").load().migrate();
        seed(source);
        execute(
            source,
            "UPDATE characters SET level = 42, experience = 700, "
            + "force_medals = 5, kill_medals = 6, supply_medals = 7 WHERE id = 1"
        );
        execute(source, "UPDATE characters SET deleted_at = CURRENT_TIMESTAMP WHERE id = 2");

        var migration = Flyway.configure().dataSource(source).defaultSchema("currency_upgrade").load();
        migration.migrate();

        assertEquals(2, scalar(source, "SELECT count(*) FROM accounts WHERE cash = 0"));
        assertEquals(3, scalar(source, "SELECT count(*) FROM characters WHERE gold = 0"));
        assertEquals(
            1,
            scalar(
                source,
                "SELECT count(*) FROM accounts WHERE id = 1 AND login = 'first' "
                + "AND credential_verifier = 'public-verifier' AND gender = 1"
            )
        );
        assertEquals(
            1,
            scalar(
                source,
                "SELECT count(*) FROM characters WHERE id = 1 AND account_id = 1 AND name = 'FirstHero' "
                + "AND level = 42 AND experience = 700 AND force_medals = 5 AND kill_medals = 6 "
                + "AND supply_medals = 7 AND deleted_at IS NULL"
            )
        );
        assertEquals(1, scalar(source, "SELECT count(*) FROM characters WHERE id = 2 AND deleted_at IS NOT NULL"));

        execute(source, "UPDATE accounts SET cash = 17 WHERE id = 1");
        execute(source, "UPDATE characters SET gold = 23 WHERE id = 1");
        migration.migrate();

        assertEquals(17, scalar(source, "SELECT cash FROM accounts WHERE id = 1"));
        assertEquals(23, scalar(source, "SELECT gold FROM characters WHERE id = 1"));
    }

    @Test
    void initializesNewBalancesToZero() throws SQLException {
        assertEquals(2, scalar(database.source(), "SELECT count(*) FROM accounts WHERE cash = 0"));
        assertEquals(3, scalar(database.source(), "SELECT count(*) FROM characters WHERE gold = 0"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 4294967295L})
    void commitsBoundaryBalancesAndReloadsThemThroughFreshConnections(long balance) throws SQLException {
        try (var connection = database.source().getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertEquals(1, statement.executeUpdate("UPDATE accounts SET cash = " + balance + " WHERE id = 1"));
            assertEquals(1, statement.executeUpdate("UPDATE characters SET gold = " + balance + " WHERE id = 1"));
            connection.commit();
        }

        var reloaded = freshSource();

        assertEquals(balance, scalar(reloaded, "SELECT cash FROM accounts WHERE id = 1"));
        assertEquals(balance, scalar(reloaded, "SELECT gold FROM characters WHERE id = 1"));
    }

    @ParameterizedTest
    @CsvSource({
        "accounts, cash, -1, 23514",
        "accounts, cash, 4294967296, 23514",
        "accounts, cash, NULL, 23502",
        "characters, gold, -1, 23514",
        "characters, gold, 4294967296, 23514",
        "characters, gold, NULL, 23502"
    })
    void rejectsInvalidBalancesWithoutChangingCommittedValues(
        String table,
        String column,
        String value,
        String sqlState
    ) throws SQLException {
        var failure = assertThrows(
            SQLException.class,
            () -> execute(database.source(), "UPDATE " + table + " SET " + column + " = " + value + " WHERE id = 1")
        );

        assertEquals(sqlState, failure.getSQLState());
        assertEquals(0, scalar(database.source(), "SELECT " + column + " FROM " + table + " WHERE id = 1"));
    }

    @Test
    void keepsAccountCashAndEachCharactersGoldIndependent() throws SQLException {
        execute(database.source(), "UPDATE accounts SET cash = 50 WHERE id = 1");
        execute(database.source(), "UPDATE characters SET gold = id * 11");

        var reloaded = freshSource();

        assertEquals(50, scalar(reloaded, "SELECT cash FROM accounts WHERE id = 1"));
        assertEquals(0, scalar(reloaded, "SELECT cash FROM accounts WHERE id = 2"));
        assertEquals(11, scalar(reloaded, "SELECT gold FROM characters WHERE id = 1 AND account_id = 1"));
        assertEquals(22, scalar(reloaded, "SELECT gold FROM characters WHERE id = 2 AND account_id = 1"));
        assertEquals(33, scalar(reloaded, "SELECT gold FROM characters WHERE id = 3 AND account_id = 2"));
    }

    @Test
    void readsFreshOwnedBalancesWithoutReturningForeignOrArchivedCharacters() throws SQLException {
        var repository = database.characters();
        database.execute("UPDATE accounts SET cash = 123 WHERE id = 1");
        database.execute("UPDATE characters SET gold = 456 WHERE id = 1");
        var first = repository.findBalances(new AccountId(1), new CharacterId(1)).orElseThrow();

        assertEquals(123, first.cash());
        assertEquals(456, first.gold());
        assertTrue(repository.findBalances(new AccountId(2), new CharacterId(1)).isEmpty());
        assertTrue(repository.findBalances(new AccountId(1), new CharacterId(999)).isEmpty());

        database.execute("UPDATE accounts SET cash = 4294967295 WHERE id = 1");
        database.execute("UPDATE characters SET gold = 789 WHERE id = 1");
        var second = database.characters().findBalances(new AccountId(1), new CharacterId(1)).orElseThrow();

        assertEquals(4294967295L, second.cash());
        assertEquals(789, second.gold());
        assertEquals(789, scalar(database.source(), "SELECT gold FROM characters WHERE id = 1"));

        database.execute("UPDATE characters SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1");

        assertTrue(repository.findBalances(new AccountId(1), new CharacterId(1)).isEmpty());
    }

    private static void seed(PGSimpleDataSource source) throws SQLException {
        execute(
            source,
            "INSERT INTO accounts (login, credential_verifier, gender) "
            + "VALUES ('first', 'public-verifier', 1), ('second', 'public-verifier', 2)"
        );
        execute(
            source,
            "INSERT INTO characters (account_id, name) "
            + "VALUES (1, 'FirstHero'), (1, 'SecondHero'), (2, 'ThirdHero')"
        );
    }

    private static PGSimpleDataSource freshSource() {
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        return source;
    }

    private static void execute(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static long scalar(PGSimpleDataSource source, String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }
}
