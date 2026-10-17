package taan.online;

import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import taan.online.account.model.Account;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.account.security.Argon2idCredentialHasher;
import taan.online.account.service.model.ClientCredential;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.repository.jdbc.JdbcCharacterRepository;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.model.CreateCharacterCommand;
import taan.online.character.service.model.CreateCharacterResult;
import taan.online.persistence.DatabaseMigrator;

public final class CharacterTestDatabase implements AutoCloseable {
    private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-bookworm");
    private final PGSimpleDataSource source = new PGSimpleDataSource();
    private final CredentialVerifier verifier = new Argon2idCredentialHasher(19456, 2, 1)
        .hash(new ClientCredential(TestFixtures.CREDENTIAL));

    public CharacterTestDatabase() {
        postgres.start();
        source.setURL(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        new DatabaseMigrator().migrate(source);
    }

    public PGSimpleDataSource source() {
        return source;
    }

    public void reset() throws SQLException {
        execute("TRUNCATE character_appearance_parts, characters, accounts RESTART IDENTITY");
    }

    public Account account(String login, AccountGender gender) {
        return new JdbcAccountRepository(source, 5).tryCreate(new LoginName(login), verifier, gender).orElseThrow();
    }

    public JdbcCharacterRepository characters() {
        return new JdbcCharacterRepository(source, 5);
    }

    public CreateCharacterResult create(AccountId owner, String name, AccountGender gender) {
        return new CreateCharacterAction(
            ignored -> Optional.of(owner),
            characters(),
            CharacterFixtures.filter(),
            CharacterFixtures.options()
        ).execute(UUID.randomUUID(), new CreateCharacterCommand(name, CharacterFixtures.appearance(gender)));
    }

    public long seed(AccountId owner, String name, CharacterAppearance appearance) throws SQLException {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            long id;

            try (var statement = connection.prepareStatement(
                "INSERT INTO characters (account_id, name) VALUES (?, ?) RETURNING id"
            )) {
                statement.setLong(1, owner.value());
                statement.setString(2, name);

                try (var rows = statement.executeQuery()) {
                    rows.next();
                    id = rows.getLong(1);
                }
            }

            try (var statement = connection.prepareStatement(
                "INSERT INTO character_appearance_parts "
                    + "(character_id, slot, item_id, parameter0, parameter1, parameter2) VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                for (var part : appearance.parts()) {
                    statement.setLong(1, id);
                    statement.setInt(2, part.slot());
                    statement.setLong(3, part.itemId());
                    statement.setInt(4, part.parameter0());
                    statement.setInt(5, part.parameter1());
                    statement.setInt(6, part.parameter2());
                    statement.addBatch();
                }

                statement.executeBatch();
            }

            connection.commit();
            return id;
        }
    }

    public long scalar(String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
            var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    public void execute(String sql) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public ApplicationConfig configuration() throws Exception {
        var environment = TestFixtures.environment();
        environment.put("DB_HOSTNAME", postgres.getHost());
        environment.put("DB_PORT", postgres.getMappedPort(5432).toString());
        environment.put("POSTGRES_DB", postgres.getDatabaseName());
        environment.put("POSTGRES_USER", postgres.getUsername());
        environment.put("POSTGRES_PASSWORD", postgres.getPassword());
        environment.put("BACKEND_BIND_ADDRESS", "127.0.0.1");
        environment.put("BACKEND_SHUTDOWN_TIMEOUT_SECONDS", "4");

        try (var available = new ServerSocket(0)) {
            environment.put("BACKEND_PORT", Integer.toString(available.getLocalPort()));
        }

        return ApplicationConfig.fromEnvironment(environment);
    }

    @Override
    public void close() {
        postgres.stop();
    }
}
