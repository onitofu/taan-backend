package taan.online.account.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;

class JdbcAccountRepositoryUnitTest {
    @Test
    void mapsRowsClosesResourcesAndCommitsBeforeReturning() {
        var database = new JdbcTestDatabase();
        var repository = new JdbcAccountRepository(database.source(), 5);

        assertTrue(repository.existsByLogin(new LoginName(TestFixtures.LOGIN)));

        var account = repository.tryCreate(
            new LoginName(TestFixtures.LOGIN),
            new CredentialVerifier(TestFixtures.VERIFIER),
            AccountGender.FEMALE
        ).orElseThrow();

        assertEquals(1, account.id().value());
        assertEquals(TestFixtures.LOGIN, account.login().value());
        assertEquals(AccountGender.FEMALE, account.gender());
        assertTrue(database.calls().indexOf("commit") > database.calls().indexOf("rows.close"));
        assertEquals("close", database.calls().getLast());

        database.hasRow(false);

        assertFalse(repository.existsByLogin(new LoginName(TestFixtures.LOGIN)));
        assertTrue(
            repository.tryCreate(
                new LoginName(TestFixtures.LOGIN),
                new CredentialVerifier(TestFixtures.VERIFIER),
                AccountGender.MALE
            ).isEmpty()
        );
    }

    @Test
    void rollsBackFailedStatementsAndCommitWithoutExposingDatabaseMessages() {
        for (String stage : List.of("getConnection", "setAutoCommit", "executeQuery", "commit", "close")) {
            var database = new JdbcTestDatabase();
            database.fail(stage);
            var repository = new JdbcAccountRepository(database.source(), 5);
            var failure = assertThrows(
                AccountPersistenceException.class,
                () -> repository.tryCreate(
                    new LoginName(TestFixtures.LOGIN),
                    new CredentialVerifier(TestFixtures.VERIFIER),
                    AccountGender.MALE
                )
            );

            assertFalse(failure.getMessage().contains("secret-value"));
            assertFalse(failure.getCause().getMessage().contains("secret-value"));

            if (stage.equals("executeQuery") || stage.equals("commit")) {
                assertTrue(database.calls().contains("rollback"));
            }
        }

        var database = new JdbcTestDatabase();
        database.fail("executeQuery");
        database.failRollback(true);
        var repository = new JdbcAccountRepository(database.source(), 5);

        assertThrows(
            AccountPersistenceException.class,
            () -> repository.tryCreate(
                new LoginName(TestFixtures.LOGIN),
                new CredentialVerifier(TestFixtures.VERIFIER),
                AccountGender.MALE
            )
        );
        assertTrue(database.calls().contains("rollback"));
        assertThrows(
            AccountPersistenceException.class,
            () -> repository.existsByLogin(new LoginName(TestFixtures.LOGIN))
        );
    }

    @Test
    void mapsBothStoredGendersAndRollsBackUnknownValues() {
        for (short value : new short[]{1, 2, 0, 3}) {
            var database = new JdbcTestDatabase();
            database.gender(value);
            var repository = new JdbcAccountRepository(database.source(), 5);

            if (value == 1 || value == 2) {
                AccountGender gender = value == 1 ? AccountGender.MALE : AccountGender.FEMALE;

                assertEquals(
                    gender,
                    repository.tryCreate(
                        new LoginName(TestFixtures.LOGIN),
                        new CredentialVerifier(TestFixtures.VERIFIER),
                        gender
                    ).orElseThrow().gender()
                );
                assertEquals(value, database.boundGender());
            } else {
                var failure = assertThrows(
                    AccountPersistenceException.class,
                    () -> repository.tryCreate(
                        new LoginName(TestFixtures.LOGIN),
                        new CredentialVerifier(TestFixtures.VERIFIER),
                        AccountGender.MALE
                    )
                );

                assertTrue(database.calls().contains("rollback"));
                assertFalse(database.calls().contains("commit"));
                assertFalse(failure.getCause().getMessage().contains(TestFixtures.VERIFIER));
            }
        }
    }
}
