package taan.online.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.reason;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import taan.online.LogCapture;
import taan.online.account.logging.AccountLogEvent;
import taan.online.bootstrap.logging.ServerLogEvent;
import taan.online.server.logging.ConnectionLogEvent;

class EventLoggerTest {
    @ParameterizedTest
    @MethodSource("events")
    void emitsCatalogMetadataUsingTheSourceLogger(LogEvent event, String name, Level level, String message) {
        try (var logs = new LogCapture(EventLoggerTest.class.getName())) {
            EventLogger.forClass(EventLoggerTest.class).log(event);

            assertEquals(1, logs.size());
            var entry = logs.events().getFirst();

            assertEquals(EventLoggerTest.class.getName(), entry.getLoggerName());
            assertEquals(level, entry.getLevel());
            assertEquals(message, entry.getFormattedMessage());
            assertEquals(1, entry.getKeyValuePairs().size());
            assertEquals("event", entry.getKeyValuePairs().getFirst().key);
            assertEquals(name, entry.getKeyValuePairs().getFirst().value);
            assertNull(entry.getThrowableProxy());
        }
    }

    @Test
    void sanitizesFailuresIncludingDatabaseCausesAndSuppressedSecrets() {
        var failure = new IllegalStateException("secret-login", new SQLException("secret-credential secret-verifier"));
        failure.addSuppressed(new RuntimeException("secret-token"));

        try (var logs = new LogCapture()) {
            EventLogger.forClass(EventLoggerTest.class).failure(
                AccountLogEvent.REGISTRATION_FAILED,
                failure,
                durationMs(12)
            );

            assertEquals(1, logs.size());
            assertTrue(logs.text().contains("account.registration.failed"));
            assertTrue(logs.text().contains("durationMs=\"12\""));
            assertTrue(logs.text().contains(IllegalStateException.class.getName()));
            assertTrue(logs.text().contains(SQLException.class.getName()));
            assertFalse(logs.text().contains("secret-"));

            var safe = logs.events().getFirst().getThrowableProxy();

            assertEquals(IllegalStateException.class.getName(), safe.getMessage());
            assertEquals(SQLException.class.getName(), safe.getCause().getMessage());
            assertEquals(0, safe.getSuppressed().length);
        }
    }

    @Test
    void skipsDisabledEventsAndExceptionWrapping() {
        var failure = new CountedLogException();
        var log = EventLogger.forClass(EventLoggerTest.class);

        try (var logs = new LogCapture(EventLoggerTest.class.getName())) {
            var backend = (Logger) LoggerFactory.getLogger(EventLoggerTest.class);
            backend.setLevel(Level.OFF);

            log.log(ServerLogEvent.READY);
            log.failure(ServerLogEvent.STARTUP_FAILED, failure);

            assertEquals(0, logs.size());
            assertEquals(0, failure.stackTraceReads());

            backend.setLevel(Level.ERROR);
            log.failure(ServerLogEvent.STARTUP_FAILED, failure);

            assertEquals(1, logs.size());
            assertEquals(1, failure.stackTraceReads());
            assertFalse(logs.text().contains("secret-message"));
        }
    }

    @Test
    void preservesContextWithoutLeakingItIntoTheNextTask() {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.clear();

        try (var logs = new LogCapture()) {
            var log = EventLogger.forClass(EventLoggerTest.class);

            try (var context = new LogContext("connection-1", "operation-1")) {
                log.log(AccountLogEvent.REGISTRATION_REJECTED, reason("ALREADY_EXISTS"));
            }

            log.log(ServerLogEvent.STOPPING);

            assertEquals(
                Map.of("connectionId", "connection-1", "operationId", "operation-1"),
                logs.events().getFirst().getMDCPropertyMap()
            );
            assertTrue(logs.events().getLast().getMDCPropertyMap().isEmpty());
            assertNull(MDC.getCopyOfContextMap());
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            }
        }
    }

    private static Stream<Arguments> events() {
        return Stream.of(
            Arguments.of(ServerLogEvent.STARTING, "server.starting", Level.INFO, "Server starting"),
            Arguments.of(ServerLogEvent.READY, "server.ready", Level.INFO, "Server ready"),
            Arguments.of(ServerLogEvent.STOPPING, "server.stopping", Level.INFO, "Server stopping"),
            Arguments.of(ServerLogEvent.STOPPED, "server.stopped", Level.INFO, "Server stopped"),
            Arguments.of(
                ServerLogEvent.SHUTDOWN_TIMEOUT,
                "server.shutdown.timeout",
                Level.WARN,
                "Shutdown deadline exceeded"
            ),
            Arguments.of(
                ServerLogEvent.INVALID_CONFIGURATION,
                "server.startup.failed",
                Level.ERROR,
                "Invalid configuration"
            ),
            Arguments.of(
                ServerLogEvent.STARTUP_FAILED,
                "server.startup.failed",
                Level.ERROR,
                "Server startup or execution failed"
            ),
            Arguments.of(ConnectionLogEvent.OPENED, "connection.opened", Level.DEBUG, "Connection opened"),
            Arguments.of(ConnectionLogEvent.CLOSED, "connection.closed", Level.DEBUG, "Connection closed"),
            Arguments.of(ConnectionLogEvent.FAILED, "connection.failed", Level.ERROR, "Connection failed"),
            Arguments.of(
                ConnectionLogEvent.PACKET_RECEIVED,
                "protocol.packet.received",
                Level.TRACE,
                "Packet received"
            ),
            Arguments.of(
                AccountLogEvent.CHECK_COMPLETED,
                "account.check.completed",
                Level.DEBUG,
                "Account availability checked"
            ),
            Arguments.of(AccountLogEvent.CHECK_FAILED, "account.check.failed", Level.ERROR, "Account operation failed"),
            Arguments.of(
                AccountLogEvent.REGISTRATION_SUCCEEDED,
                "account.registration.succeeded",
                Level.INFO,
                "Account created"
            ),
            Arguments.of(
                AccountLogEvent.REGISTRATION_REJECTED,
                "account.registration.rejected",
                Level.INFO,
                "Registration rejected"
            ),
            Arguments.of(
                AccountLogEvent.REGISTRATION_FAILED,
                "account.registration.failed",
                Level.ERROR,
                "Account operation failed"
            ),
            Arguments.of(
                AccountLogEvent.REQUEST_CANCELLED,
                "account.request.cancelled",
                Level.DEBUG,
                "Queued account request cancelled"
            )
        );
    }
}
