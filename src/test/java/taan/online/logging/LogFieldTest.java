package taan.online.logging;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.attackKind;
import static taan.online.logging.LogField.connectionId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.lootPickerScalar;
import static taan.online.logging.LogField.operationId;
import static taan.online.logging.LogField.outcome;
import static taan.online.logging.LogField.packetType;
import static taan.online.logging.LogField.port;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.remoteAddress;
import static taan.online.logging.LogField.rootBytes;
import static taan.online.logging.LogField.setting;
import static taan.online.logging.LogField.weaponId;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.account.logging.AccountLogEvent;
import taan.online.bootstrap.logging.ServerLogEvent;
import taan.online.server.logging.ConnectionLogEvent;

class LogFieldTest {
    @Test
    void preservesFixedStartupAndOperationFieldNamesAndScalarTypes() {
        assertEquals(Map.of("event", "server.ready", "port", 2180), fields(ServerLogEvent.READY, port(2180)));
        assertEquals(Map.of("event", "server.ready", "weaponId", 8, "attackKind", 2),
            fields(ServerLogEvent.READY, weaponId(8), attackKind(2)));
        assertEquals(Map.of("event", "server.ready"), fields(ServerLogEvent.READY, weaponId(null)));
        assertEquals(
            Map.of("event", "server.ready", "lootPickerScalar", 70),
            fields(ServerLogEvent.READY, lootPickerScalar(70))
        );
        assertEquals(
            Map.of("event", "server.startup.failed", "setting", "POSTGRES_PASSWORD"),
            fields(ServerLogEvent.INVALID_CONFIGURATION, setting("POSTGRES_PASSWORD"))
        );
        assertEquals(
            Map.of("event", "account.registration.succeeded", "accountId", 4294967295L, "outcome", "CREATED"),
            fields(AccountLogEvent.REGISTRATION_SUCCEEDED, accountId(4294967295L), outcome("CREATED"))
        );
        assertEquals(
            Map.of("event", "account.registration.failed", "durationMs", 14L),
            fields(AccountLogEvent.REGISTRATION_FAILED, durationMs(14))
        );
    }

    @Test
    void preservesConnectionAndPacketMetadataWithoutPacketContents() {
        UUID connection = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        var address = new InetSocketAddress("127.0.0.1", 12345);

        assertEquals(
            Map.of("event", "connection.opened", "connectionId", connection, "remoteAddress", address.toString()),
            fields(ConnectionLogEvent.OPENED, connectionId(connection), remoteAddress(address))
        );
        assertEquals(
            Map.of(
                "event",
                "account.request.cancelled",
                "connectionId",
                connection,
                "operationId",
                operation,
                "reason",
                "PEER_CLOSED"
            ),
            fields(
                AccountLogEvent.REQUEST_CANCELLED,
                connectionId(connection),
                operationId(operation),
                reason("PEER_CLOSED")
            )
        );
        assertEquals(
            Map.of("event", "protocol.packet.received", "type", "ffffffff", "rootBytes", 128),
            fields(ConnectionLogEvent.PACKET_RECEIVED, packetType(-1), rootBytes(128))
        );
    }

    @Test
    void omitsUnavailableValues() {
        assertEquals(
            Map.of("event", "connection.opened"),
            fields(ConnectionLogEvent.OPENED, connectionId(null), remoteAddress(null))
        );
        assertEquals(
            Map.of("event", "account.request.cancelled"),
            fields(AccountLogEvent.REQUEST_CANCELLED, operationId(null), reason(null))
        );
        assertEquals(
            Map.of("event", "server.startup.failed"),
            fields(ServerLogEvent.INVALID_CONFIGURATION, setting(null))
        );
        assertEquals(
            Map.of("event", "account.check.completed"),
            fields(AccountLogEvent.CHECK_COMPLETED, outcome(null))
        );
    }

    @Test
    void escapesControlCharactersAndUnicodeLineSeparators() {
        String input = "bad\r\n\t\0\u007f\u0085\u2028\u2029value";

        try (var logs = new LogCapture()) {
            EventLogger.forClass(LogFieldTest.class).log(AccountLogEvent.REGISTRATION_REJECTED, reason(input));

            assertEquals(1, logs.size());
            assertFalse(logs.text().contains(input));
            assertFalse(logs.text().contains("\n"));
            assertTrue(logs.text().contains("bad\\u000d\\u000a\\u0009\\u0000\\u007f\\u0085\\u2028\\u2029value"));
        }
    }

    @Test
    void boundsTextAfterEscapingAndPreservesUnicodeAtTheBoundary() {
        assertEquals("", fields(AccountLogEvent.REGISTRATION_REJECTED, reason("")).get("reason"));
        assertEquals(
            "a".repeat(256),
            fields(AccountLogEvent.REGISTRATION_REJECTED, reason("a".repeat(256))).get("reason")
        );
        assertEquals(
            "a".repeat(255) + "…",
            fields(AccountLogEvent.REGISTRATION_REJECTED, reason("a".repeat(10000))).get("reason")
        );
        assertEquals(
            "a".repeat(254) + "…",
            fields(AccountLogEvent.REGISTRATION_REJECTED, reason("a".repeat(254) + "😀x")).get("reason")
        );

        String escaped = (String) fields(AccountLogEvent.REGISTRATION_REJECTED, reason("\n".repeat(256)))
            .get("reason");

        assertEquals(256, escaped.length());
        assertTrue(escaped.endsWith("…"));
        assertFalse(escaped.contains("\n"));
    }

    @Test
    void doesNotExposeFieldValuesThroughToString() {
        assertFalse(reason("sensitive-value").toString().contains("sensitive-value"));
    }

    private Map<String, Object> fields(LogEvent event, LogField... fields) {
        try (var logs = new LogCapture()) {
            EventLogger.forClass(LogFieldTest.class).log(event, fields);
            return logs.events().getFirst().getKeyValuePairs().stream()
                .collect(toMap(pair -> pair.key, pair -> pair.value));
        }
    }
}
