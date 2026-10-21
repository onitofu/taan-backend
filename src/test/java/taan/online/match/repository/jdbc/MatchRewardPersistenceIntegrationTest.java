package taan.online.match.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
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
import taan.online.RewardTestFixtures;
import taan.online.TestLevels;
import taan.online.account.model.AccountGender;
import taan.online.character.model.GameCharacter;
import taan.online.match.repository.MatchSettlement;

class MatchRewardPersistenceIntegrationTest {
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
    void commitsPrepaidCardsGoldExperienceAndMedalsOnceAndReloadsTheirExactStatistics() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var result = RewardTestFixtures.result(first.id().value(), second.id().value(), 99);
        database.execute("UPDATE characters SET experience = 900, gold = 123");
        database.execute("UPDATE accounts SET cash = 456");
        var barrier = new CyclicBarrier(4);
        var tasks = new ArrayList<Callable<MatchSettlement>>();

        for (int index = 0; index < 4; index++) {
            tasks.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return new JdbcMatchResultRepository(database.source(), 5, TestLevels.table()).settle(result);
            });
        }

        try (var workers = Executors.newFixedThreadPool(4)) {
            int created = 0;

            for (var task : workers.invokeAll(tasks, 15, TimeUnit.SECONDS)) {
                created += task.get(1, TimeUnit.SECONDS).created() ? 1 : 0;
            }

            assertEquals(1, created);
        }

        var winner = database.characters().findByAccount(first.accountId()).getFirst();
        var loser = database.characters().findByAccount(second.accountId()).getFirst();
        var replay = new JdbcMatchResultRepository(database.source(), 5, TestLevels.table()).settle(result);

        assertEquals(2260, winner.progress().experience());
        assertEquals(158, winner.gold());
        assertEquals(2, winner.level());
        assertEquals(1082, loser.progress().experience());
        assertEquals(139, loser.gold());
        assertEquals(912, database.scalar("SELECT sum(cash) FROM accounts"));
        assertEquals(1, winner.progress().forceMedals());
        assertEquals(1, winner.progress().killMedals());
        assertFalse(replay.created());
        assertEquals(99, replay.result().participants().getFirst().card().draw());
        assertEquals(200, replay.result().participants().getFirst().card().amount());
        assertEquals(0.5f, replay.result().participants().getFirst().shots().accuracy());
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
    }

    @Test
    void rollsBackEveryParticipantOnGoldOverflowAndDoesNotPayAgainAfterALostCommitReply() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var result = RewardTestFixtures.result(first.id().value(), second.id().value(), 0);
        database.execute("UPDATE characters SET gold = 4294967295 WHERE id = " + second.id().value());
        var repository = new JdbcMatchResultRepository(database.source(), 5, TestLevels.table());

        assertThrows(IllegalArgumentException.class, () -> repository.settle(result));
        assertEquals(0, database.scalar("SELECT sum(experience) FROM characters"));
        assertEquals(0, database.scalar("SELECT sum(force_medals) FROM characters"));
        assertEquals(0, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(0, database.scalar("SELECT gold FROM characters WHERE id = " + first.id().value()));

        database.execute("UPDATE characters SET gold = 0");
        var lostReply = new JdbcMatchResultRepository(
            LostCommitReplyDataSource.wrap(database.source()),
            5,
            TestLevels.table()
        );
        var recovered = lostReply.settle(result);

        assertFalse(recovered.created());
        assertEquals(0, recovered.result().participants().getFirst().card().draw());
        assertEquals(1160, database.characters().findByAccount(first.accountId()).getFirst().progress().experience());
        assertEquals(36, database.characters().findByAccount(first.accountId()).getFirst().gold());
        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
    }

    @Test
    void rejectsIncompleteShotCardAndWalletGroupsWithoutChangingTheLedger() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var result = RewardTestFixtures.result(first.id().value(), second.id().value(), 99);
        new JdbcMatchResultRepository(database.source(), 5, TestLevels.table()).settle(result);

        for (String assignments : new String[]{
            "damaging_shots = accepted_shots + 1", "accepted_shots = NULL", "gold_before = NULL",
            "gold_after = gold_after + 1", "card_draw = 100", "card_kind = NULL", "card_amount = card_amount + 1",
            "experience_card_delta = experience_card_delta + 1",
            "gold_before = NULL, gold_after = NULL, gold_base_delta = NULL, "
                + "gold_medal_delta = NULL, gold_card_delta = NULL"
        }) {
            var failure = assertThrows(
                SQLException.class,
                () -> database.execute(
                    "UPDATE match_participant_results SET " + assignments
                        + " WHERE character_id = " + first.id().value()
                )
            );

            assertEquals("23514", failure.getSQLState());
        }

        assertEquals(1, database.scalar("SELECT count(*) FROM match_results"));
        assertEquals(
            200,
            database.scalar("SELECT card_amount FROM match_participant_results "
            + "WHERE character_id = " + first.id().value())
        );
    }

    @Test
    void upgradesHistoricalResultsWithoutInventingCardsGoldOrAccuracyOrReplayingRewards() throws Exception {
        var first = character("AlphaHero");
        var second = character("BravoHero");
        var historical = MatchResultFixtures.result(first.id().value(), second.id().value());
        new JdbcMatchResultRepository(database.source(), 5, TestLevels.table()).settle(historical);
        database.execute("CREATE SCHEMA reward_upgrade");
        var source = new PGSimpleDataSource();
        source.setURL(database.source().getURL());
        source.setUser(database.source().getUser());
        source.setPassword(database.source().getPassword());
        source.setCurrentSchema("reward_upgrade");
        Flyway.configure().dataSource(source).defaultSchema("reward_upgrade").target("13").load().migrate();

        database.execute("""
            INSERT INTO reward_upgrade.accounts (id, login, credential_verifier, gender, created_at, cash)
            OVERRIDING SYSTEM VALUE
            SELECT id, login, credential_verifier, gender, created_at, cash FROM public.accounts
            """);
        database.execute("""
            INSERT INTO reward_upgrade.characters
                (id, account_id, name, level, created_at, deleted_at, experience,
                 force_medals, kill_medals, supply_medals, gold)
            OVERRIDING SYSTEM VALUE
            SELECT id, account_id, name, level, created_at, deleted_at, experience,
                   force_medals, kill_medals, supply_medals, gold FROM public.characters
            """);
        database.execute("""
            INSERT INTO reward_upgrade.match_results
                (match_id, finished_at, winning_team, cause, policy_version, reward_duration_millis)
            SELECT match_id, finished_at, winning_team, cause, policy_version, reward_duration_millis
            FROM public.match_results
            """);

        String columns = "match_id, character_id, slot_index, team, damage, award_eligible, force_medal, kills, "
            + "kill_medal, experience_before, level_before, experience_after, level_after, experience_base_delta, "
            + "experience_pcroom_delta, experience_medal_delta, experience_guard_adjustment, force_medals_after, "
            + "kill_medals_after, supply_medals_after";
        database.execute("INSERT INTO reward_upgrade.match_participant_results (" + columns + ") SELECT "
            + columns + " FROM public.match_participant_results");
        Flyway.configure().dataSource(source).defaultSchema("reward_upgrade").load().migrate();
        var replay = new JdbcMatchResultRepository(source, 5, TestLevels.table()).settle(historical);

        assertFalse(replay.created());
        assertEquals(historical, replay.result());
        assertNull(replay.result().participants().getFirst().shots());
        assertNull(replay.result().participants().getFirst().card());
        assertNull(replay.result().participants().getFirst().goldReward());
        assertEquals(0, database.scalar("SELECT sum(gold) FROM reward_upgrade.characters"));
        assertEquals(2, database.scalar("SELECT sum(force_medals) FROM reward_upgrade.characters"));
        assertEquals(
            0,
            database.scalar("SELECT sum(experience_card_delta) "
            + "FROM reward_upgrade.match_participant_results")
        );
    }

    private GameCharacter character(String name) throws SQLException {
        var account = database.account(name + "@example.test", AccountGender.MALE);
        database.seed(account.id(), name, CharacterFixtures.appearance(AccountGender.MALE));
        return database.characters().findByAccount(account.id()).getFirst();
    }
}
