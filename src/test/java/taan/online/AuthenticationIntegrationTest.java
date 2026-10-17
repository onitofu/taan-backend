package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import taan.online.account.model.LoginName;
import taan.online.account.model.AccountGender;
import taan.online.account.security.Argon2idCredentialHasher;
import taan.online.account.service.model.ClientCredential;
import taan.online.account.repository.jdbc.JdbcAccountRepository;
import taan.online.bootstrap.Application;
import taan.online.bootstrap.ApplicationConfig;
import taan.online.persistence.DatabaseMigrator;

class AuthenticationIntegrationTest {
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

    @Test
    void authenticatesThroughFreshConnectionsAndRetainsIdentityAcrossRestart() throws Exception {
        var configuration = configuration();
        long accountId;

        try (var application = new Application(configuration)) {
            application.start();

            try (var registration = connect(configuration.port())) {
                readRoot(registration.getInputStream());
                write(registration, TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 2, 0));

                assertEquals(0, readRoot(registration.getInputStream())[4]);
                assertEquals(-1, registration.getInputStream().read());
            }

            accountId = repository().findByLogin(new LoginName(TestFixtures.LOGIN)).orElseThrow().id().value();

            try (var first = connect(configuration.port())) {
                loginHandshake(first);
                write(first, TestFixtures.login(TestFixtures.LOGIN.toUpperCase(), TestFixtures.CREDENTIAL));
                byte[] accepted = readRoot(first.getInputStream());

                assertEquals(1, accepted[4]);
                assertEquals(
                    accountId,
                    Integer.toUnsignedLong(
                        ByteBuffer.wrap(accepted).order(ByteOrder.LITTLE_ENDIAN).getInt(21)
                    )
                );
                assertEquals(2, accepted[32]);

                var other = repository().tryCreate(
                    new LoginName("other@example.test"),
                    new Argon2idCredentialHasher(19456, 2, 1).hash(new ClientCredential(TestFixtures.CREDENTIAL)),
                    AccountGender.MALE
                ).orElseThrow();

                try (var independent = connect(configuration.port())) {
                    loginHandshake(independent);
                    write(independent, TestFixtures.login("other@example.test", TestFixtures.CREDENTIAL));
                    byte[] independentReply = readRoot(independent.getInputStream());

                    assertEquals(1, independentReply[4]);
                    assertEquals(1, independentReply[32]);
                    assertEquals(
                        other.id().value(),
                        Integer.toUnsignedLong(
                            ByteBuffer.wrap(independentReply).order(ByteOrder.LITTLE_ENDIAN).getInt(21)
                        )
                    );
                }

                try (var duplicate = connect(configuration.port())) {
                    loginHandshake(duplicate);
                    write(duplicate, TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
                    byte[] rejected = readRoot(duplicate.getInputStream());

                    assertEquals(2, ByteBuffer.wrap(rejected).order(ByteOrder.LITTLE_ENDIAN).getInt(5));
                    assertEquals(-1, duplicate.getInputStream().read());
                }

                first.shutdownOutput();

                assertEquals(-1, first.getInputStream().read());

                try (var reconnected = connect(configuration.port())) {
                    loginHandshake(reconnected);
                    write(reconnected, TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));

                    assertEquals(1, readRoot(reconnected.getInputStream())[4]);
                }
            }

            try (var wrong = connect(configuration.port())) {
                loginHandshake(wrong);
                write(wrong, TestFixtures.login(TestFixtures.LOGIN, "0".repeat(32)));
                byte[] rejected = readRoot(wrong.getInputStream());

                assertEquals(7, ByteBuffer.wrap(rejected).order(ByteOrder.LITTLE_ENDIAN).getInt(5));
                assertEquals(-1, wrong.getInputStream().read());
            }
        }

        try (var application = new Application(configuration); var next = startedSocket(
            application,
            configuration.port()
        )) {
            loginHandshake(next);
            write(next, TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            byte[] accepted = readRoot(next.getInputStream());

            assertEquals(1, accepted[4]);
            assertEquals(
                accountId,
                Integer.toUnsignedLong(
                    ByteBuffer.wrap(accepted).order(ByteOrder.LITTLE_ENDIAN).getInt(21)
                )
            );
        }
    }

    private static void loginHandshake(Socket socket) throws IOException {
        assertEquals(21, readRoot(socket.getInputStream()).length);

        write(socket, TestFixtures.fixture("first-packet-minimal.root.hex"));
        byte[] reply = readRoot(socket.getInputStream());

        assertEquals(10, reply.length);
        assertEquals(0, reply[4]);
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

}
