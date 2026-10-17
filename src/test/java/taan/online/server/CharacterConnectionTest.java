package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.account.model.AccountGender;
import taan.online.character.repository.jdbc.CharacterPersistenceException;
import taan.online.session.service.SessionRegistry;

class CharacterConnectionTest {
    @Test
    void serializesChecksAndCreationThroughResponseCompletionAndKeepsExpectedRejectionsOpen() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            authenticate(harness);
            harness.channel().writeInbound(CharacterFixtures.confirm("FirstHero", 0));
            harness.channel().writeInbound(request(true));

            assertEquals(1, harness.jobs().size());
            assertEquals(0, harness.characters().inserts);

            harness.run();

            assertEquals(0, harness.reply());
            assertEquals(1, harness.jobs().size());
            assertEquals(0, harness.characters().inserts);

            harness.run();

            assertEquals(0, harness.reply());
            assertEquals(1, harness.characters().commits);
            assertTrue(harness.channel().isActive());

            harness.channel().writeInbound(request(true));
            harness.run();

            assertEquals(2, harness.reply());
            assertTrue(harness.channel().isActive());
            assertEquals(1, harness.characters().commits);
            assertFalse(logs.text().contains("Luma"));
            assertFalse(logs.text().contains("FirstHero"));
            assertTrue(logs.text().contains("character.creation.rejected"));
        }
    }

    @Test
    void rejectsWrongPhasesLocalAccountsAndMalformedBodiesWithoutDatabaseWork() {
        for (boolean creation : new boolean[]{false, true}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                harness.channel().writeInbound(request(creation));

                assertFalse(harness.channel().isActive());
                assertTrue(harness.jobs().isEmpty());
                assertEquals(0, harness.characters().checks);
                assertEquals(0, harness.characters().transactions);
            }

            for (boolean badSlot : new boolean[]{false, true}) {
                try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                    authenticate(harness);
                    byte[] root = request(creation);

                    if (badSlot) {
                        root[4] = 1;
                    } else {
                        root = Arrays.copyOf(root, root.length - 1);
                    }

                    harness.channel().writeInbound(root);
                    harness.run();

                    assertFalse(harness.channel().isActive());
                    assertNull(harness.channel().readOutbound());
                    assertEquals(0, harness.characters().checks);
                    assertEquals(0, harness.characters().transactions);
                    assertTrue(logs.text().contains("protocol.frame.rejected"));
                    assertFalse(logs.text().contains("character.creation.failed"));
                }
            }
        }
    }

    @Test
    void closesInfrastructureFailuresAndLogsEachExceptionOnceWithSafeAsyncContext() {
        for (boolean creation : new boolean[]{false, true}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                authenticate(harness);
                harness.characters().failure = new CharacterPersistenceException(
                    "database",
                    new SQLException("Luma " + TestFixtures.CREDENTIAL + " private-database-value")
                );

                try (var logs = new LogCapture()) {
                    harness.channel().writeInbound(request(creation));
                    harness.run();

                    assertFalse(harness.channel().isActive());
                    assertNull(harness.channel().readOutbound());
                    String eventName = creation ? "character.creation.failed" : "character.name.check.failed";
                    var failures = logs.events().stream().filter(
                        event -> event.getKeyValuePairs().stream().anyMatch(
                            field -> field.key.equals("event") && field.value.equals(eventName)
                        )
                    ).toList();

                    assertEquals(1, failures.size());
                    assertTrue(failures.getFirst().getMDCPropertyMap().containsKey("sessionId"));
                    assertTrue(failures.getFirst().getMDCPropertyMap().containsKey("operationId"));
                    assertFalse(logs.text().contains("connection.failed"));
                    assertFalse(logs.text().contains("Luma"));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                    assertFalse(logs.text().contains("private-database-value"));
                }
            }
        }
    }

    @Test
    void removesQueuedCharacterWorkAfterDisconnectOrTimeout() {
        for (boolean creation : new boolean[]{false, true}) {
            for (boolean timeout : new boolean[]{false, true}) {
                try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                    authenticate(harness);
                    harness.channel().writeInbound(request(creation));

                    assertEquals(1, harness.jobs().size());

                    harness.disconnect(timeout);

                    assertTrue(harness.jobs().isEmpty());
                    assertEquals(0, harness.characters().checks);
                    assertEquals(0, harness.characters().transactions);
                    assertTrue(logs.text().contains("character.request.cancelled"));
                }
            }
        }
    }

    @Test
    void ignoresResultsForInvalidatedSessionsWithoutRenewingIdleActivity() {
        for (boolean creation : new boolean[]{false, true}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                authenticate(harness);
                harness.channel().advanceTimeBy(250, TimeUnit.SECONDS);
                harness.channel().writeInbound(request(creation));
                harness.invalidateSession();
                harness.run();

                assertTrue(harness.channel().isActive());
                assertNull(harness.channel().readOutbound());
                assertEquals(0, harness.characters().checks);
                assertEquals(0, harness.characters().transactions);

                harness.channel().advanceTimeBy(51, TimeUnit.SECONDS);
                harness.channel().runScheduledPendingTasks();

                assertFalse(harness.channel().isActive());
            }
        }
    }

    @Test
    void finishesStartedTransactionsButDiscardsLateRepliesAndClosedCheckResults() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            authenticate(harness);
            harness.characters().onInsert = () -> harness.channel().unsafe().close(harness.channel().newPromise());
            harness.channel().writeInbound(request(true));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertEquals(1, harness.characters().commits);
            assertEquals(1, harness.characters().stored.size());
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            authenticate(harness);
            harness.characters().onRead = () -> harness.channel().unsafe().close(harness.channel().newPromise());
            harness.channel().writeInbound(request(false));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertEquals(1, harness.characters().checks);
        }
    }

    @Test
    void releasesTheLoginClaimWhenTheCatalogCannotBeLoaded() {
        var sessions = new SessionRegistry();

        try (var harness = new ConnectionContextTestHarness(8, sessions)) {
            handshake(harness);
            harness.repository().exists(true);
            harness.characters().failure = new IllegalStateException("private-catalog-value");
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.run();

            assertEquals(0, harness.reply());
            assertFalse(harness.channel().isActive());
        }

        try (var reconnected = new ConnectionContextTestHarness(8, sessions)) {
            authenticate(reconnected);
        }
    }

    private static byte[] request(boolean creation) {
        return creation ? CharacterFixtures.create("Luma", AccountGender.FEMALE, 0)
            : CharacterFixtures.confirm("Luma", 0);
    }

    private static void handshake(ConnectionContextTestHarness harness) {
        harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
        harness.run();

        assertEquals(0, harness.reply());
    }

    private static void authenticate(ConnectionContextTestHarness harness) {
        handshake(harness);
        harness.repository().exists(true);
        harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
        harness.run();

        assertEquals(1, harness.reply());
    }
}
