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
import taan.online.TestLevels;
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
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchParticipantStatistics;
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
    void upgradesLegacyLevelsAndNormalizesArchivedAndOverCapProgress() throws Exception {
        database.execute("CREATE SCHEMA experience_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("experience_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("experience_upgrade").target("12").load().migrate();

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO accounts (login, credential_verifier, gender) "
                + "VALUES ('legacy', 'public', 1)");
            statement.execute("INSERT INTO characters (account_id, name, level, experience, force_medals) VALUES "
                + "(1, 'LegacyOne', 42, 0, 5), (1, 'LegacyTwo', 200, 0, 0), "
                + "(1, 'LegacyThree', 1, 6000, 0), (1, 'LegacyFour', 1, 4294967295, 0)");
            statement.execute("UPDATE characters SET deleted_at = CURRENT_TIMESTAMP WHERE name = 'LegacyOne'");
        }

        new DatabaseMigrator().migrate(source);
        new DatabaseMigrator().migrate(source);

        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(
                "SELECT level, experience, force_medals, deleted_at FROM characters ORDER BY id"
            )) {
            assertTrue(rows.next());
            assertEquals(42, rows.getInt("level"));
            assertEquals(TestLevels.table().threshold(42), rows.getLong("experience"));
            assertEquals(5, rows.getLong("force_medals"));
            assertTrue(rows.getObject("deleted_at") != null);
            assertTrue(rows.next());
            assertEquals(109, rows.getInt("level"));
            assertEquals(1048576, rows.getLong("experience"));
            assertTrue(rows.next());
            assertEquals(4, rows.getInt("level"));
            assertEquals(6000, rows.getLong("experience"));
            assertTrue(rows.next());
            assertEquals(109, rows.getInt("level"));
            assertEquals(1048576, rows.getLong("experience"));
            assertFalse(rows.next());
        }
    }

    @Test
    void commitsExperienceLevelsAndTwoMedalsOnceAndPreservesCurrenciesAcrossConcurrentRetries() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        database.execute("UPDATE characters SET experience = 900, gold = 123");
        database.execute("UPDATE accounts SET cash = 456");
        var proposal = MatchExperienceJdbcTest.result(first.id().value(), second.id().value());
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Callable<MatchSettlement>>();

        for (int index = 0; index < 4; index++) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(proposal);
            });
        }

        try (var workers = Executors.newFixedThreadPool(4)) {
            var completed = workers.invokeAll(tasks, 15, TimeUnit.SECONDS);
            int created = 0;

            for (var pending : completed) {
                created += pending.get(1, TimeUnit.SECONDS).created() ? 1 : 0;
            }

            assertEquals(1, created);
        }

        var reloaded = new JdbcCharacterRepository(
            database.source(),
            5,
            new JdbcInventoryRepository(
                database.source(),
                5
            )
        )
            .findByAccount(first.accountId()).getFirst();
        assertEquals(2060, reloaded.progress().experience());
        assertEquals(2, reloaded.level());
        assertEquals(1, reloaded.progress().forceMedals());
        assertEquals(1, reloaded.progress().killMedals());
        assertEquals(0, reloaded.progress().supplyMedals());
        assertEquals(246, database.scalar("SELECT sum(gold) FROM characters"));
        assertEquals(912, database.scalar("SELECT sum(cash) FROM accounts"));
        var repository = new RewardTestRepository(database.source(), 5, TestLevels.table());
        var replayed = repository.settle(proposal);
        assertFalse(replayed.created());
        assertEquals(1160, replayed.result().participants().getFirst().experienceReward().delta());
        assertEquals(
            2060,
            replayed.result().participants().getFirst().experienceReward().after().progress().experience()
        );
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
    }

    @Test
    void preservesHistoricalExperienceAndVersionAfterAnUpgradeAndFreshRepository() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var proposal = MatchExperienceJdbcTest.result(first.id().value(), second.id().value());

        HistoricalMatchResultSeed.insert(database, proposal, first.id().value(), second.id().value());

        var repository = new RewardTestRepository(database.source(), 5, TestLevels.table());
        var replayed = repository.settle(proposal);
        var again = new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(proposal);

        assertFalse(replayed.created());
        assertFalse(again.created());
        assertEquals(replayed.result(), again.result());
        assertEquals("pvp-exp-1.0.2", replayed.result().policyVersion());
        assertEquals(116, replayed.result().participants().getFirst().experienceReward().delta());
        assertEquals(52, replayed.result().participants().getLast().experienceReward().delta());
        assertEquals(1016, database.characters().findByAccount(first.accountId()).getFirst().progress().experience());
        assertEquals(952, database.characters().findByAccount(second.accountId()).getFirst().progress().experience());
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(2, database.scalar("SELECT sum(force_medals) FROM characters"));
        assertEquals(2, database.scalar("SELECT sum(kill_medals) FROM characters"));
    }

    @Test
    void rollsBackProgressAndTheLedgerTogetherAndDoesNotPayAgainAfterALostCommitReply() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var proposal = MatchExperienceJdbcTest.result(first.id().value(), second.id().value());
        database.execute("UPDATE characters SET kill_medals = 4294967295 WHERE id = " + second.id().value());
        var repository = new RewardTestRepository(database.source(), 5, TestLevels.table());

        assertThrows(RuntimeException.class, () -> repository.settle(proposal));
        assertEquals(0, database.scalar("SELECT sum(experience) FROM characters"));
        assertEquals(0, database.scalar("SELECT sum(force_medals) FROM characters"));
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        database.execute("UPDATE characters SET kill_medals = 0");
        var lost = new RewardTestRepository(
            LostCommitReplyDataSource.wrap(database.source()),
            5,
            TestLevels.table()
        );

        assertFalse(lost.settle(proposal).created());
        assertEquals(1160, database.characters().findByAccount(first.accountId()).getFirst().progress().experience());
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(2, database.scalar("SELECT sum(kill_medals) FROM characters"));
    }

    @Test
    void serializesDistinctConcurrentMatchesWithoutLosingProgressOrMedals() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var proposals = List.of(
            MatchExperienceJdbcTest.result(first.id().value(), second.id().value()),
            MatchExperienceJdbcTest.result(second.id().value(), first.id().value())
        );
        var barrier = new CyclicBarrier(2);
        var tasks = new ArrayList<Callable<MatchSettlement>>();

        for (var proposal : proposals) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(proposal);
            });
        }

        try (var workers = Executors.newFixedThreadPool(2)) {
            for (var future : workers.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
                assertTrue(future.get(1, TimeUnit.SECONDS).created());
            }
        }

        assertEquals(2, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(3374, database.scalar("SELECT sum(experience) FROM characters"));
        assertEquals(4, database.scalar("SELECT sum(force_medals) FROM characters"));
        assertEquals(4, database.scalar("SELECT sum(kill_medals) FROM characters"));
    }

    @Test
    void preservesTheEarnedLevelOnPassiveLossAndStopsAccumulatingExperienceAtTheCap() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        database.execute("UPDATE characters SET experience = 945, level = 2");
        var input = MatchExperienceJdbcTest.result(first.id().value(), second.id().value());
        var members = new ArrayList<>(input.participants());
        var loser = members.getLast();
        members.set(
            1,
            new MatchParticipantResult(
                loser.characterId(),
                loser.slotIndex(),
                loser.team(),
                0,
                true,
                false,
                0,
                false,
                new MatchParticipantStatistics(1000, 0, 6000, false),
                null
            )
        );
        var proposal = new MatchResult(
            input.matchId(),
            input.finishedAt(),
            input.winningTeam(),
            input.cause(),
            input.policyVersion(),
            members,
            60000L
        );
        var repository = new RewardTestRepository(database.source(), 5, TestLevels.table());
        var reward = repository.settle(proposal).result().participants().getLast().experienceReward();

        assertEquals(-5, reward.delta());
        assertEquals(940, reward.after().progress().experience());
        assertEquals(2, reward.after().level());
        assertEquals(0, reward.pcroomDelta());
        assertEquals(0, reward.medalDelta());
        assertEquals(2, database.characters().findByAccount(second.accountId()).getFirst().level());
        database.execute("UPDATE characters SET experience = 1048570, level = 108");
        repository.settle(MatchExperienceJdbcTest.result(first.id().value(), second.id().value()));

        assertEquals(2097152, database.scalar("SELECT sum(experience) FROM characters"));
        assertEquals(218, database.scalar("SELECT sum(level) FROM characters"));
    }

    @Test
    void migratesLegacyMedalDefaultsAndPreservesTheExistingLevel() throws Exception {
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
        assertEquals(new CharacterProgress(TestLevels.table().threshold(42), 0, 0, 0), reloaded.progress());
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
                return new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(result);
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
        var replay = new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(changedProposal);

        assertFalse(replay.created());
        assertEquals(result, replay.result());
        assertEquals(6, database.characters().findByAccount(second.accountId()).getFirst().progress().forceMedals());

        var nextMatch = MatchResultFixtures.result(second.id().value(), first.id().value());
        new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(nextMatch);

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
            () -> new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(result)
        );
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(0, database.scalar("SELECT count(*) FROM match_participant_results"));
        assertEquals(0, database.characters().findByAccount(first.accountId()).getFirst().progress().forceMedals());

        var invalid = MatchResultFixtures.result(first.id().value(), 0xffffffffL);

        assertThrows(
            MatchPersistenceException.class,
            () -> new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(invalid)
        );
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        database.execute("UPDATE characters SET force_medals = 0");

        assertTrue(new RewardTestRepository(database.source(), 5, TestLevels.table()).settle(result).created());
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
                return new RewardTestRepository(database.source(), 5, TestLevels.table())
                    .settle(MatchResultFixtures.result(first.id().value(), second.id().value()));
            });
            var b = workers.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new RewardTestRepository(database.source(), 5, TestLevels.table())
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
        var repository = new RewardTestRepository(
            LostCommitReplyDataSource.wrap(database.source()),
            5,
            TestLevels.table()
        );
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
