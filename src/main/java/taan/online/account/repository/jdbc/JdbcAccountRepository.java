package taan.online.account.repository.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.AccountRepository;

public final class JdbcAccountRepository implements AccountRepository {
    private final DataSource dataSource;
    private final int queryTimeoutSeconds;

    public JdbcAccountRepository(DataSource dataSource, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    @Override
    public boolean existsByLogin(LoginName login) {
        try (var connection = dataSource.getConnection();
            var statement = connection.prepareStatement("SELECT EXISTS (SELECT 1 FROM accounts WHERE login = ?)")) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(1, login.value());

            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        } catch (SQLException failure) {
            throw new AccountPersistenceException("existsByLogin", failure);
        }
    }

    @Override
    public Optional<Account> tryCreate(LoginName login, CredentialVerifier verifier, AccountGender gender) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                Optional<Account> account = insert(connection, login, verifier, gender);
                connection.commit();
                return account;
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }

                throw failure;
            }
        } catch (SQLException failure) {
            throw new AccountPersistenceException("tryCreate", failure);
        }
    }

    private short databaseGender(AccountGender gender) {
        return (short) switch (gender) {
            case MALE -> 1;
            case FEMALE -> 2;
        };
    }

    private AccountGender accountGender(short value) throws SQLException {
        return switch (value) {
            case 1 -> AccountGender.MALE;
            case 2 -> AccountGender.FEMALE;
            default -> throw new SQLException("Unsupported stored account gender", "22000");
        };
    }

    private Optional<Account> insert(
        Connection connection,
        LoginName login,
        CredentialVerifier verifier,
        AccountGender gender
    ) throws SQLException {
        String sql = "INSERT INTO accounts (login, credential_verifier, gender) VALUES (?, ?, ?) "
            + "ON CONFLICT (login) DO NOTHING RETURNING id, login, credential_verifier, gender, created_at";
        Optional<Account> account;

        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setString(1, login.value());
            statement.setString(2, verifier.encoded());
            statement.setShort(3, databaseGender(gender));

            try (var rows = statement.executeQuery()) {
                if (rows.next()) {
                    account = Optional.of(
                        new Account(
                            new AccountId(rows.getLong("id")),
                            new LoginName(rows.getString("login")),
                            new CredentialVerifier(rows.getString("credential_verifier")),
                            accountGender(rows.getShort("gender")),
                            rows.getObject("created_at", OffsetDateTime.class).toInstant()
                        )
                    );
                } else {
                    account = Optional.empty();
                }
            }
        }

        return account;
    }
}
