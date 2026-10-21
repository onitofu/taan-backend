package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import taan.online.CharacterFixtures;
import taan.online.CharacterTestDatabase;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.character.model.CharacterName;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.GameCharacter;
import taan.online.character.repository.jdbc.JdbcCharacterRepository;
import taan.online.inventory.repository.jdbc.JdbcInventoryRepository;
import taan.online.match.model.MatchOutcomeCause;
import taan.online.match.model.MatchResult;
import taan.online.match.repository.MatchSettlement;
import taan.online.persistence.DatabaseMigrator;

class MatchResultPersistenceIntegrationTest {
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
    }

    @Test
    void addsZeroCountersToExistingCharactersWithoutResettingTheirLevel() throws Exception {
        database.execute("CREATE SCHEMA progress_upgrade");
        var isolated = new PGSimpleDataSource();
        isolated.setURL(database.source().getURL());
        isolated.setUser(database.source().getUser());
        isolated.setPassword(database.source().getPassword());
        isolated.setCurrentSchema("progress_upgrade");
        Flyway.configure().dataSource(isolated).defaultSchema("progress_upgrade").target("10").load().migrate();
        var account = new JdbcAccountRepository(isolated, 5).tryCreate(
            new LoginName("migration@example.test"),
            new CredentialVerifier(TestFixtures.VERIFIER),
            AccountGender.MALE
        ).orElseThrow();
        var characters = new JdbcCharacterRepository(isolated, 5, new JdbcInventoryRepository(isolated, 5));

        try (var transaction = characters.beginTransaction(account.id())) {
            transaction.tryInsert(new CharacterName("LegacyHero"), CharacterFixtures.starter(AccountGender.MALE));
            transaction.commit();
        }

        try (var connection = isolated.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE characters SET level = 42");
        }

        new DatabaseMigrator().migrate(isolated);
        new DatabaseMigrator().migrate(isolated);
        var reloaded = characters.findByAccount(account.id()).getFirst();

        assertEquals(42, reloaded.level());
        assertEquals(CharacterProgress.initial(), reloaded.progress());
    }

    @Test
    void commitsAndReloadsMedalsOnceAcrossConcurrentRetriesAndFreshRepositories() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        database.execute(
            "UPDATE characters SET experience = 700, force_medals = 5, kill_medals = 6, supply_medals = 7"
        );
        var result = MatchResultFixtures.result(first.id().value(), second.id().value());
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Callable<MatchSettlement>>();

        for (int index = 0; index < 4; index++) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new JdbcMatchResultRepository(database.source(), 5).settle(result);
            });
        }

        try (var workers = Executors.newFixedThreadPool(4)) {
            var completed = workers.invokeAll(tasks, 15, TimeUnit.SECONDS);
            int created = 0;

            for (var pending : completed) {
                var stored = pending.get(1, TimeUnit.SECONDS);
                created += stored.created() ? 1 : 0;
                assertEquals(result, stored.result());
            }

            assertEquals(1, created);
        }

        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(2, database.scalar("SELECT count(*) FROM match_participant_results"));
        assertEquals(
            new CharacterProgress(700, 6, 6, 7),
            database.characters().findByAccount(first.accountId()).getFirst().progress()
        );

        var changedProposal = new MatchResult(
            result.matchId(),
            result.finishedAt(),
            Optional.empty(),
            MatchOutcomeCause.TECHNICAL_CANCELLATION,
            "different-policy",
            List.of()
        );
        var replay = new JdbcMatchResultRepository(database.source(), 5).settle(changedProposal);

        assertFalse(replay.created());
        assertEquals(result, replay.result());
        assertEquals(6, database.characters().findByAccount(second.accountId()).getFirst().progress().forceMedals());

        var nextMatch = MatchResultFixtures.result(second.id().value(), first.id().value());
        new JdbcMatchResultRepository(database.source(), 5).settle(nextMatch);

        assertEquals(7, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());
        assertEquals(7, database.characters().findByAccount(second.accountId()).getFirst().progress().forceMedals());
    }

    @Test
    void rollsBackAllRecipientsAndTheLedgerOnOverflowOrAnInvalidCharacter() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        database.execute("UPDATE characters SET force_medals = 4294967295 WHERE id = " + second.id().value());
        var result = MatchResultFixtures.result(first.id().value(), second.id().value());

        assertThrows(
            MatchPersistenceException.class,
            () -> new JdbcMatchResultRepository(database.source(), 5).settle(result)
        );
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(0, database.scalar("SELECT count(*) FROM match_participant_results"));
        assertEquals(0, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());

        var invalid = MatchResultFixtures.result(first.id().value(), 0xffffffffL);

        assertThrows(
            MatchPersistenceException.class,
            () -> new JdbcMatchResultRepository(database.source(), 5).settle(invalid)
        );
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        database.execute("UPDATE characters SET force_medals = 0");

        assertTrue(new JdbcMatchResultRepository(database.source(), 5).settle(result).created());
        assertEquals(1, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());

        for (String column : List.of("experience", "force_medals", "kill_medals", "supply_medals")) {
            for (long value : new long[]{-1, 0x100000000L}) {
                assertThrows(
                    SQLException.class,
                    () -> database.execute("UPDATE characters SET " + column + " = " + value)
                );
            }
        }
    }

    @Test
    void accumulatesConcurrentDifferentMatchesInsteadOfOverwritingAStaleCharacterSnapshot() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var barrier = new CyclicBarrier(2);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new JdbcMatchResultRepository(database.source(), 5)
                    .settle(MatchResultFixtures.result(first.id().value(), second.id().value()));
            });
            var b = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new JdbcMatchResultRepository(database.source(), 5)
                    .settle(MatchResultFixtures.result(second.id().value(), first.id().value()));
            });

            assertTrue(a.get(15, TimeUnit.SECONDS).created());
            assertTrue(b.get(15, TimeUnit.SECONDS).created());
        }

        assertEquals(2, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());
        assertEquals(2, database.characters().findByAccount(second.accountId()).getFirst().progress().forceMedals());
    }

    @Test
    void recoversALostCommitReplyByReadingTheExistingResultWithoutIssuingAnotherMedal() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var result = MatchResultFixtures.result(first.id().value(), second.id().value());
        var repository = new JdbcMatchResultRepository(LostCommitReplyDataSource.wrap(database.source()), 5);
        var recovered = repository.settle(result);

        assertFalse(recovered.created());
        assertEquals(result, recovered.result());
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(1, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());
        assertEquals(1, database.characters().findByAccount(second.accountId()).getFirst().progress().forceMedals());
    }

    private GameCharacter character(String name) throws SQLException {
        var account = database.account(name + "@example.test", AccountGender.MALE);
        database.seed(account.id(), name, CharacterFixtures.appearance(AccountGender.MALE));
        return database.characters().findByAccount(account.id()).getFirst();
    }
}
