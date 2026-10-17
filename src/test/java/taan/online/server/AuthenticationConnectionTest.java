package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.network.FrameAssemblyTimeoutEvent;
import taan.online.network.FrameDecoder;
import taan.online.session.service.SessionRegistry;

class AuthenticationConnectionTest {
    @Test
    void closesOnHandshakeInfrastructureFailureWithoutSendingAnUnrelatedReply() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(
            8,
            new SessionRegistry(),
            true,
            Clock.fixed(Instant.MAX, ZoneOffset.UTC)
        )) {
            harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
            harness.run();

            assertFalse(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertTrue(logs.text().contains("connection.failed"));
            assertFalse(logs.text().contains("account.login.failed"));
        }
    }

    @Test
    void reassemblesFragmentedHandshakeAndCoalescedLoginFrames() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.repository().exists(true);
            harness.channel().pipeline().addFirst(new FrameDecoder(16384, 10));
            byte[] first = TestFixtures.fixture("first-packet-minimal.root.hex");
            byte[] login = TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL);
            byte[] stream = ByteBuffer.allocate(8 + first.length + login.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(first.length).put(first).putInt(login.length).put(login).array();
            harness.channel().writeInbound(Unpooled.wrappedBuffer(stream, 0, 3));

            assertTrue(harness.jobs().isEmpty());

            harness.channel().writeInbound(Unpooled.wrappedBuffer(stream, 3, stream.length - 3));
            harness.run();

            assertEquals(0, harness.reply());

            harness.run();

            assertEquals(1, harness.reply());
        }
    }

    @Test
    void cleansUpAnInactiveHandlerAndIgnoresLateTimeoutAfterDisconnect() throws Exception {
        try (var harness = new ConnectionContextTestHarness(8, new SessionRegistry(), false)) {
            harness.channel().close();
            harness.handler().userEventTriggered(harness.context(), FrameAssemblyTimeoutEvent.INSTANCE);

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
        }
    }

    @Test
    void serializesHandshakeLoginAndKeepAliveAndIgnoresRepeatedMalformedLogin() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            harness.repository().exists(true);
            harness.channel().writeInbound(TestFixtures.fixture("first-packet-minimal.root.hex"));
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.channel().writeInbound(keepAlive());
            harness.run();

            assertEquals(0, harness.reply());
            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(1, harness.reply());

            harness.run();
            harness.channel().writeInbound(new byte[]{0x59, 0x04, (byte) 0x8b, 0x19});

            assertTrue(harness.jobs().isEmpty());

            assertTrue(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());
            assertTrue(logs.text().contains("account.login.succeeded"));
            assertTrue(logs.text().contains("account.login.ignored"));
            assertTrue(logs.text().contains("sessionId="));
            assertNull(MDC.get("sessionId"));

            harness.channel().close();

            assertTrue(logs.text().contains("session.ended"));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    @Test
    void mapsExpectedAndInfrastructureFailuresAndDiscardsQueuedLogin() {
        for (int code : new int[]{0, 1, 7, -1}) {
            try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                handshake(harness);
                harness.repository().exists(code != 1);

                if (code == -1) {
                    harness.repository().failure(new IllegalStateException(TestFixtures.VERIFIER));
                }

                byte[] request = TestFixtures.login(
                    TestFixtures.LOGIN,
                    code == 7 ? "0".repeat(32)
                    : code == 0 ? "invalid" : TestFixtures.CREDENTIAL
                );
                harness.channel().writeInbound(request);
                harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
                harness.run();
                ByteBuf reply = harness.channel().readOutbound();

                assertEquals(0, reply.getUnsignedByte(8));
                assertEquals(Math.max(0, code), reply.getIntLE(9));

                reply.release();
                assertFalse(harness.channel().isActive());
                assertTrue(harness.jobs().isEmpty());
                assertNull(harness.channel().readOutbound());
                assertTrue(logs.text().contains(code == -1 ? "account.login.failed" : "account.login.rejected"));
                assertFalse(logs.text().contains(TestFixtures.LOGIN));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
            }
        }
    }

    @Test
    void releasesClaimOnTransportFailureAndRejectsDuplicateOnlineAccount() {
        var sessions = new SessionRegistry();

        try (var first = new ConnectionContextTestHarness(8, sessions);
            var second = new ConnectionContextTestHarness(8, sessions)) {
            authenticate(first);
            handshake(second);
            second.repository().exists(true);
            second.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            second.run();
            ByteBuf reply = second.channel().readOutbound();

            assertEquals(2, reply.getIntLE(9));

            reply.release();
            first.handler().exceptionCaught(first.context(), new IOException("transport reset"));

            try (var next = new ConnectionContextTestHarness(8, sessions)) {
                authenticate(next);
                first.handler().channelInactive(first.context());

                try (var duplicate = new ConnectionContextTestHarness(8, sessions)) {
                    handshake(duplicate);
                    duplicate.repository().exists(true);
                    duplicate.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
                    duplicate.run();
                    ByteBuf rejected = duplicate.channel().readOutbound();

                    assertEquals(2, rejected.getIntLE(9));

                    rejected.release();
                }
            }
        }
    }

    @Test
    void idleTimeoutReleasesClaimWhileKeepAliveRenewsDeadlineWithoutReply() {
        var sessions = new SessionRegistry();

        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8, sessions)) {
            authenticate(harness);
            harness.channel().freezeTime();
            advance(harness, 250);
            harness.channel().writeInbound(keepAlive());
            harness.run();
            advance(harness, 250);

            assertTrue(harness.channel().isActive());
            assertNull(harness.channel().readOutbound());

            advance(harness, 51);

            assertFalse(harness.channel().isActive());
            assertTrue(logs.text().contains("IDLE_TIMEOUT"));
            assertTrue(logs.text().contains("session.ended"));

            try (var next = new ConnectionContextTestHarness(8, sessions)) {
                authenticate(next);
            }
        }
    }

    @Test
    void incompleteFramesAndIgnoredLoginDoNotRenewActivity() {
        for (boolean fragment : new boolean[]{false, true}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                authenticate(harness);
                harness.channel().freezeTime();
                advance(harness, 299);

                if (fragment) {
                    harness.channel().pipeline().addFirst(new FrameDecoder(16384, 10));
                    harness.channel().writeInbound(Unpooled.wrappedBuffer(new byte[]{4}));
                } else {
                    harness.channel().writeInbound(new byte[]{0x59, 0x04, (byte) 0x8b, 0x19});

                    assertTrue(harness.jobs().isEmpty());
                }

                advance(harness, 2);

                assertFalse(harness.channel().isActive());
            }
        }
    }

    @Test
    void closesInvalidStatesAndMalformedLoginWithoutInfrastructureError() {
        for (int scenario = 0; scenario < 5; scenario++) {
            try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                if (scenario != 0) {
                    handshake(harness);
                }

                byte[] request = switch (scenario) {
                    case 0 -> TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL);
                    case 1 -> TestFixtures.fixture("first-packet-minimal.root.hex");
                    case 2 -> TestFixtures.check(TestFixtures.LOGIN, 0);
                    case 3 -> TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0);
                    default -> new byte[]{0x59, 0x04, (byte) 0x8b, 0x19};
                };
                harness.channel().writeInbound(request);

                if (!harness.jobs().isEmpty()) {
                    harness.run();
                }

                assertFalse(harness.channel().isActive());
                assertTrue(logs.text().contains("protocol.frame.rejected"));
                assertFalse(logs.text().contains("account.login.failed"));
            }
        }
    }

    @Test
    void disconnectDuringCredentialReadCannotClaimAccountOrSendReply() {
        var sessions = new SessionRegistry();

        try (var harness = new ConnectionContextTestHarness(8, sessions)) {
            handshake(harness);
            harness.repository().exists(true);
            harness.repository().onCheck(() -> harness.channel().close());
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.run();

            assertNull(harness.channel().readOutbound());

            try (var next = new ConnectionContextTestHarness(8, sessions)) {
                authenticate(next);
            }
        }
    }

    @Test
    void sendFailureReleasesClaimAndQueuedLoginIsCancelledOnTimeout() {
        var sessions = new SessionRegistry();

        try (var harness = new ConnectionContextTestHarness(8, sessions)) {
            handshake(harness);
            harness.repository().exists(true);
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.run();
            delay.fail();
            harness.channel().runPendingTasks();

            assertFalse(harness.channel().isActive());

            try (var next = new ConnectionContextTestHarness(8, sessions)) {
                authenticate(next);
            }
        }

        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            handshake(harness);
            harness.channel().writeInbound(TestFixtures.login(TestFixtures.LOGIN, TestFixtures.CREDENTIAL));
            harness.disconnect(true);

            assertTrue(harness.jobs().isEmpty());
            assertTrue(logs.text().contains("account.login.cancelled"));
        }
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

    private static byte[] keepAlive() {
        return new byte[]{0x37, 0x04, (byte) 0x89, 0x18};
    }

    private static void advance(ConnectionContextTestHarness harness, int seconds) {
        harness.channel().advanceTimeBy(seconds, TimeUnit.SECONDS);
        harness.channel().runScheduledPendingTasks();
    }
}
