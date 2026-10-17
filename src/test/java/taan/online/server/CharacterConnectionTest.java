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
import taan.online.account.model.AccountId;
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
        for (int operation : new int[]{0, 1, 2}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                harness.channel().writeInbound(request(operation));

                assertFalse(harness.channel().isActive());
                assertTrue(harness.jobs().isEmpty());
                assertEquals(0, harness.characters().checks);
                assertEquals(0, harness.characters().transactions);
            }

            for (boolean badSlot : new boolean[]{false, true}) {
                try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                    authenticate(harness);
                    byte[] root = request(operation);

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
        for (int operation : new int[]{0, 1, 2}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                authenticate(harness);
                harness.characters().failure = new CharacterPersistenceException(
                    "database",
                    new SQLException("Luma " + TestFixtures.CREDENTIAL + " private-database-value")
                );

                try (var logs = new LogCapture()) {
                    harness.channel().writeInbound(request(operation));
                    harness.run();

                    assertFalse(harness.channel().isActive());
                    assertNull(harness.channel().readOutbound());
                    String eventName = switch (operation) {
                        case 0 -> "character.name.check.failed";
                        case 1 -> "character.creation.failed";
                        default -> "character.deletion.failed";
                    };
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
        for (int operation : new int[]{0, 1, 2}) {
            for (boolean timeout : new boolean[]{false, true}) {
                try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                    authenticate(harness);
                    harness.channel().writeInbound(request(operation));

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
        for (int operation : new int[]{0, 1, 2}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                authenticate(harness);
                harness.channel().advanceTimeBy(250, TimeUnit.SECONDS);
                harness.channel().writeInbound(request(operation));
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

    @Test
    void serializesDeletionAndNameReuseAndDiscardsAlreadyCommittedRepliesAfterDisconnect() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            authenticate(harness);
            harness.characters().stored.add(
                CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma")
            );
            harness.channel().writeInbound(CharacterFixtures.delete(1001, 0));
            harness.channel().writeInbound(CharacterFixtures.confirm("Luma", 0));

            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(0, harness.reply());
            assertEquals(1, harness.characters().archived.size());
            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(0, harness.reply());
            assertTrue(harness.channel().isActive());

            harness.channel().writeInbound(CharacterFixtures.delete(1001, 0));
            harness.run();

            assertEquals(1, harness.reply());
            assertTrue(harness.channel().isActive());
            assertFalse(logs.text().contains("Luma"));
            assertTrue(logs.text().contains("character.deletion.succeeded"));
            assertTrue(logs.text().contains("character.deletion.rejected"));
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            authenticate(harness);
            harness.characters().stored.add(
                CharacterFixtures.character(1001, new AccountId(1), AccountGender.FEMALE, "Luma")
            );
            harness.characters().onArchive = () -> harness.channel().unsafe().close(harness.channel().newPromise());
            harness.channel().writeInbound(CharacterFixtures.delete(1001, 0));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertEquals(1, harness.characters().commits);
            assertEquals(1, harness.characters().archived.size());
        }
    }

    private static byte[] request(int operation) {
        return switch (operation) {
            case 0 -> request(false);
            case 1 -> request(true);
            default -> CharacterFixtures.delete(1001, 0);
        };
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
