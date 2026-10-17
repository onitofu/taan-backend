package taan.online;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import taan.online.account.model.AccountGender;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.model.LoginName;
import taan.online.account.repository.jdbc.AccountPersistenceException;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.account.security.Argon2idCredentialHasher;
import taan.online.account.service.model.ClientCredential;
import taan.online.bootstrap.Application;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.TcpServer;
import taan.online.network.TcpServerSettings;
import taan.online.persistence.DatabaseMigrator;

class RegistrationIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-bookworm");
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        POSTGRES.start();
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        new DatabaseMigrator().migrate(dataSource);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @BeforeEach
    void clearAccounts() throws Exception {
        executeSql(
            "TRUNCATE inventory_items, inventories, characters, accounts RESTART IDENTITY"
        );
    }

    @Test
    void migratesIdempotentlyAndReloadsCommittedAccountsWithFreshConnections() throws Exception {
        var hasher = new Argon2idCredentialHasher(19456, 2, 1);
        var verifier = hasher.hash(new ClientCredential(TestFixtures.CREDENTIAL));
        var first = repository().tryCreate(new LoginName("CASE@Example.Test"), verifier, AccountGender.MALE)
            .orElseThrow();
        var second = repository().tryCreate(
            new LoginName("other@example.test"),
            hasher.hash(new ClientCredential(TestFixtures.CREDENTIAL)),
            AccountGender.FEMALE
        ).orElseThrow();

        assertEquals(AccountGender.MALE, first.gender());
        assertEquals(AccountGender.FEMALE, second.gender());
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.credentialVerifier(), second.credentialVerifier());
        assertTrue(repository().existsByLogin(new LoginName("case@example.test")));
        assertTrue(repository().tryCreate(new LoginName("CASE@Example.Test"), verifier, AccountGender.MALE).isEmpty());

        new DatabaseMigrator().migrate(dataSource);

        try (var connection = dataSource.getConnection();
            var statement = connection.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
            statement.setLong(1, first.id().value());

            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("case@example.test", rows.getString("login"));
                assertEquals(verifier.encoded(), rows.getString("credential_verifier"));
                assertEquals(1, rows.getShort("gender"));
                assertEquals(first.createdAt(), rows.getTimestamp("created_at").toInstant());
            }
        }
    }

    @Test
    void enforcesDatabaseChecksAndUnsignedIdentityRange() throws Exception {
        for (String sql : new String[]{
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('UPPER', 'test', 1)",
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('with space', 'test', 1)",
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('', 'test', 1)",
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('name', 'test', 0)",
            "INSERT INTO accounts (login, credential_verifier, gender) VALUES ('name', NULL, 1)",
            "INSERT INTO accounts (id, login, credential_verifier, gender) OVERRIDING SYSTEM VALUE "
                + "VALUES (4294967296, 'name', 'test', 1)"
        }) {
            assertThrows(SQLException.class, () -> executeSql(sql));
        }

        executeSql("SELECT setval(pg_get_serial_sequence('accounts', 'id'), 4294967295, false)");

        assertEquals(
            4294967295L,
            repository().tryCreate(
                new LoginName("last"),
                new CredentialVerifier(TestFixtures.VERIFIER),
                AccountGender.MALE
            ).orElseThrow().id().value()
        );
        assertThrows(
            AccountPersistenceException.class,
            () -> repository().tryCreate(
                new LoginName("overflow"),
                new CredentialVerifier(TestFixtures.VERIFIER),
                AccountGender.MALE
            )
        );
    }

    @Test
    void rollsBackRejectedDatabaseOperationsWithoutLeakingValues() throws Exception {
        executeSql(
            "CREATE FUNCTION reject_registration() RETURNS trigger LANGUAGE plpgsql AS $$ "
            + "BEGIN RAISE EXCEPTION 'secret-database-value'; END $$"
        );
        executeSql(
            "CREATE TRIGGER reject_registration BEFORE INSERT ON accounts "
            + "FOR EACH ROW EXECUTE FUNCTION reject_registration()"
        );

        try {
            var failure = assertThrows(
                AccountPersistenceException.class,
                () -> repository().tryCreate(
                    new LoginName(TestFixtures.LOGIN),
                    new CredentialVerifier(TestFixtures.VERIFIER),
                    AccountGender.MALE
                )
            );

            assertFalse(failure.getCause().getMessage().contains("secret-database-value"));
            assertFalse(repository().existsByLogin(new LoginName(TestFixtures.LOGIN)));
        } finally {
            executeSql("DROP TRIGGER reject_registration ON accounts");
            executeSql("DROP FUNCTION reject_registration()");
        }
    }

    @Test
    void allowsOnlyOneConcurrentRegistrationForCanonicalLogin() throws Exception {
        try (var threads = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var results = new ArrayList<Future<Boolean>>();

            for (String name : new String[]{"Race@Example.Test", "race@example.test"}) {
                results.add(
                    threads.submit(
                        () -> {
                            barrier.await(5, TimeUnit.SECONDS);
                            return repository().tryCreate(
                                new LoginName(name),
                                new CredentialVerifier(TestFixtures.VERIFIER),
                                AccountGender.FEMALE
                            ).isPresent();
                        }
                    )
                );
            }

            int winners = 0;

            for (var result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    winners++;
                }
            }

            assertEquals(1, winners);
        }
    }

    @Test
    void servesRealTcpAndPreservesAccountAcrossApplicationRestart() throws Exception {
        var configuration = configuration();

        try (var application = new Application(configuration)) {
            application.start();

            try (var socket = connect(configuration.port())) {
                assertEquals(21, readRoot(socket.getInputStream()).length);

                write(socket, TestFixtures.fixture("check-local-account.root.hex"));

                assertArrayEquals(
                    TestFixtures.fixture("PrCheckAccount-status1-selector0.root.hex"),
                    readRoot(socket.getInputStream())
                );

                write(socket, TestFixtures.fixture("create-probe_only.root.hex"));

                assertArrayEquals(
                    TestFixtures.fixture("PrCreateAccount-status0-selector0.root.hex"),
                    readRoot(socket.getInputStream())
                );
                assertEquals(-1, socket.getInputStream().read());
            }

            application.close();
            application.awaitTermination();
        }

        try (var restarted = new Application(configuration)) {
            restarted.start();

            try (var socket = connect(configuration.port())) {
                readRoot(socket.getInputStream());
                write(socket, TestFixtures.fixture("check-local-account.root.hex"));

                assertArrayEquals(
                    TestFixtures.fixture("PrCheckAccount-status0-selector0.root.hex"),
                    readRoot(socket.getInputStream())
                );

                write(socket, new byte[]{0x21, 0x05, 0x0e, 0x23});

                assertEquals(-1, socket.getInputStream().read());
            }
        }

        assertTrue(repository().existsByLogin(new LoginName(TestFixtures.LOGIN)));
    }

    @Test
    void releasesTheOnlyTcpSlotAfterAnIncompleteFrameExpires() throws Exception {
        var environment = TestFixtures.environment();
        var configured = configuration();
        environment.put("DB_HOSTNAME", POSTGRES.getHost());
        environment.put("DB_PORT", POSTGRES.getMappedPort(5432).toString());
        environment.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        environment.put("POSTGRES_USER", POSTGRES.getUsername());
        environment.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
        environment.put("BACKEND_BIND_ADDRESS", "127.0.0.1");
        environment.put("BACKEND_PORT", Integer.toString(configured.port()));
        environment.put("BACKEND_MAX_CONNECTIONS", "1");
        environment.put("BACKEND_FRAME_ASSEMBLY_TIMEOUT_SECONDS", "1");

        try (var application = new Application(ApplicationConfig.fromEnvironment(environment));
            var slow = startedSocket(application, configured.port())) {
            readRoot(slow.getInputStream());
            slow.getOutputStream().write(4);
            slow.getOutputStream().flush();

            assertEquals(-1, slow.getInputStream().read());

            try (var next = connect(configured.port())) {
                assertEquals(21, readRoot(next.getInputStream()).length);
            }
        }
    }

    @Test
    void boundsShutdownAndPreservesInterruption() throws Exception {
        executeSql(
            "CREATE FUNCTION delay_registration() RETURNS trigger LANGUAGE plpgsql AS $$ "
            + "BEGIN PERFORM pg_sleep(3); RETURN NEW; END $$"
        );
        executeSql(
            "CREATE TRIGGER delay_registration BEFORE INSERT ON accounts "
            + "FOR EACH ROW EXECUTE FUNCTION delay_registration()"
        );

        try {
            for (boolean interrupt : new boolean[]{false, true}) {
                awaitDatabaseIdle();
                var configuration = configuration();

                try (var application = new Application(configuration); var socket = startedSocket(
                    application,
                    configuration.port()
                )) {
                    readRoot(socket.getInputStream());
                    write(socket, TestFixtures.create("shutdown-" + interrupt, TestFixtures.CREDENTIAL, 1, 0));
                    awaitDatabaseWork();

                    if (interrupt) {
                        Thread.currentThread().interrupt();
                    }

                    long started = System.nanoTime();
                    application.close();

                    assertTrue(System.nanoTime() - started < Duration.ofSeconds(5).toNanos());
                    assertEquals(interrupt, Thread.interrupted());
                }
            }
        } finally {
            executeSql("DROP TRIGGER delay_registration ON accounts");
            executeSql("DROP FUNCTION delay_registration()");
        }
    }

    @Test
    void releasesNetworkResourcesBeforeBindingAndOnBindFailure() throws Exception {
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(getClass()),
            1,
            Duration.ofSeconds(1),
            System::nanoTime
        );
        var settings = new TcpServerSettings("127.0.0.1", 0, 1, 1024, 5, 5, 5);

        try (var server = new TcpServer(settings, warnings, ChannelInboundHandlerAdapter::new)) {
            server.stopAccepting();
        }

        try (var occupied = new ServerSocket(0);
            var server = new TcpServer(
                new TcpServerSettings("127.0.0.1", occupied.getLocalPort(), 1, 1024, 5, 5, 5),
                warnings,
                ChannelInboundHandlerAdapter::new
            )) {
            assertThrows(IOException.class, server::start);
        }
    }

    private static void awaitDatabaseIdle() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();

        while (System.nanoTime() < deadline) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery(
                    "SELECT count(*) FROM pg_stat_activity "
                    + "WHERE wait_event = 'PgSleep'"
                )) {
                rows.next();

                if (rows.getInt(1) == 0) {
                    return;
                }
            }

            TimeUnit.MILLISECONDS.sleep(20);
        }

        throw new AssertionError("Database operation did not finish");
    }

    private static void awaitDatabaseWork() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();

        while (System.nanoTime() < deadline) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery(
                    "SELECT EXISTS (SELECT 1 FROM pg_stat_activity "
                    + "WHERE wait_event = 'PgSleep')"
                )) {
                rows.next();

                if (rows.getBoolean(1)) {
                    return;
                }
            }

            TimeUnit.MILLISECONDS.sleep(20);
        }

        throw new AssertionError("Registration did not enter the database operation");
    }

    private static ApplicationConfig configuration() throws IOException {
        var environment = TestFixtures.environment();
        environment.put("DB_HOSTNAME", POSTGRES.getHost());
        environment.put("DB_PORT", POSTGRES.getMappedPort(5432).toString());
        environment.put("POSTGRES_DB", POSTGRES.getDatabaseName());
        environment.put("POSTGRES_USER", POSTGRES.getUsername());
        environment.put("POSTGRES_PASSWORD", POSTGRES.getPassword());
        environment.put("BACKEND_BIND_ADDRESS", "127.0.0.1");
        environment.put("BACKEND_SHUTDOWN_TIMEOUT_SECONDS", "4");

        try (var available = new ServerSocket(0)) {
            environment.put("BACKEND_PORT", Integer.toString(available.getLocalPort()));
        }

        return ApplicationConfig.fromEnvironment(environment);
    }

    private static Socket startedSocket(Application application, int port) throws Exception {
        application.start();
        return connect(port);
    }

    private static Socket connect(int port) throws IOException {
        var socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(10000);
        return socket;
    }

    private static void write(Socket socket, byte[] root) throws IOException {
        socket.getOutputStream().write(
            ByteBuffer.allocate(4 + root.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(root.length).put(root).array()
        );
        socket.getOutputStream().flush();
    }

    private static byte[] readRoot(InputStream stream) throws IOException {
        var input = new DataInputStream(stream);
        int length = Integer.reverseBytes(input.readInt());

        assertTrue(length >= 4 && length <= 1024);

        byte[] root = input.readNBytes(length);

        assertEquals(length, root.length);

        return root;
    }

    private static JdbcAccountRepository repository() {
        return new JdbcAccountRepository(dataSource, 5);
    }

    private static void executeSql(String sql) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
