package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.session.service.SessionRegistry;

class ProtocolConnectionTest {
    @Test
    void rejectsQueuedHandshakeUsingTheStateAfterThePreviousRequest() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));

            assertEquals(ProtocolPhase.INITIAL, machine(harness).phase());
            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(1, harness.reply());
            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertNull(harness.channel().readOutbound());
            assertEquals(ProtocolPhase.TERMINATED, machine(harness).phase());
        }
    }

    @Test
    void rejectedLoginTerminatesTheProtocolBeforeItsResponseIsFlushed() throws Exception {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            handshake(harness);
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.run();

            assertTrue(harness.channel().isActive());
            assertEquals(ProtocolPhase.TERMINATED, machine(harness).phase());
            assertTrue(harness.jobs().isEmpty());
            assertNull(harness.channel().readOutbound());

            harness.channel().writeInbound(new byte[]{0x59, 0x04, (byte) 0x8b, 0x19});
            delay.send();

            assertEquals(0, harness.reply());
            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertFalse(logs.text().contains("account.login.succeeded"));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    @Test
    void doesNotProcessOrDecodeRepeatedLoginUntilTheSuccessResponseCompletes() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8)) {
            handshake(harness);
            harness.repository().exists(true);
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.channel().writeInbound(new byte[]{0x59, 0x04, (byte) 0x8b, 0x19});
            harness.run();

            assertEquals(ProtocolPhase.AUTHENTICATED, machine(harness).phase());
            assertTrue(harness.jobs().isEmpty());

            delay.send();

            assertEquals(1, harness.reply());
            assertTrue(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertNull(harness.channel().readOutbound());
        }
    }

    @Test
    void dropsLoginCompletionWhenTransportClosesAfterTheWorkerClaimsAccount() throws Exception {
        var sessions = new SessionRegistry();

        try (var harness = new ConnectionContextTestHarness(8, sessions)) {
            handshake(harness);
            harness.repository().exists(true);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.jobs().remove().run();

            assertEquals(ProtocolPhase.AWAITING_LOGIN, machine(harness).phase());
            assertNull(harness.channel().readOutbound());

            harness.channel().unsafe().close(harness.channel().newPromise());
            harness.channel().runPendingTasks();

            assertEquals(ProtocolPhase.TERMINATED, machine(harness).phase());
            assertNull(harness.channel().readOutbound());

            try (var next = new ConnectionContextTestHarness(8, sessions)) {
                handshake(next);
                next.repository().exists(true);
                next.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
                next.run();

                assertEquals(1, next.reply());
            }
        }
    }

    @Test
    void discardedSessionResultNeitherAuthenticatesNorRenewsActivity() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8)) {
            handshake(harness);
            harness.channel().freezeTime();
            harness.channel().advanceTimeBy(299, TimeUnit.SECONDS);
            harness.repository().exists(true);
            harness.repository().onCheck(harness::invalidateSession);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.run();

            assertTrue(harness.channel().isActive());
            assertEquals(ProtocolPhase.AWAITING_LOGIN, machine(harness).phase());
            assertNull(harness.channel().readOutbound());

            harness.channel().advanceTimeBy(2, TimeUnit.SECONDS);
            harness.channel().runScheduledPendingTasks();

            assertFalse(harness.channel().isActive());
            assertEquals(ProtocolPhase.TERMINATED, machine(harness).phase());
        }
    }

    @Test
    void invalidWorkerOutcomeIsLoggedOnceAsAnInternalFailureAndCancelsWork() throws Exception {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            var callback = ConnectionContext.class.getDeclaredMethod(
                "complete",
                ChannelHandlerContext.class,
                RequestCompletion.class
            );
            callback.setAccessible(true);
            callback.invoke(
                harness.handler(),
                harness.context(),
                new RequestCompletion(
                    ProtocolEvent.LOGIN_SUCCEEDED,
                    CompletableFuture.completedFuture(null)
                )
            );

            assertFalse(harness.channel().isActive());
            assertEquals(ProtocolPhase.TERMINATED, machine(harness).phase());
            assertTrue(harness.jobs().isEmpty());
            assertEquals(0, harness.repository().checks());
            long errors = logs.events().stream().filter(event -> event.getLevel() == Level.ERROR).count();

            assertEquals(1, errors);
            assertTrue(logs.text().contains("connection.failed"));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    private static void handshake(ConnectionContextTestHarness harness) {
        harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
        harness.run();

        assertEquals(0, harness.reply());
    }

    private static ProtocolStateMachine machine(ConnectionContextTestHarness harness) throws Exception {
        var field = ConnectionContext.class.getDeclaredField("protocol");
        field.setAccessible(true);
        return (ProtocolStateMachine) field.get(harness.handler());
    }
}
