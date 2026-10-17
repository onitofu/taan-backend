package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.util.concurrent.EventExecutor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import taan.online.LogCapture;
import taan.online.TestFixtures;
import taan.online.network.FrameDecoder;
import taan.online.protocol.MalformedPacketException;

class ConnectionContextTest {
    @Test
    void serializesAvailabilityAndCreationThenDiscardsQueuedWork() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 2, 0));
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));

            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(1, harness.reply());
            assertEquals(1, harness.jobs().size());

            harness.run();

            assertEquals(0, harness.reply());
            assertEquals(1, harness.repository().inserts());
            assertEquals(2, harness.repository().checks());
            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());

            assertTrue(logs.text().contains("account.registration.succeeded"));
            assertTrue(logs.text().contains("operationId="));
            assertTrue(logs.text().contains("connectionId="));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));

            assertNull(MDC.get("operationId"));
        }
    }

    @Test
    void rejectsInvalidInputWithoutLosingSelectorAndContinuesWithNextRequest() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 1));
            harness.run();
            ByteBuf response = harness.channel().readOutbound();

            assertEquals(255, response.getUnsignedByte(8));
            assertEquals(1, response.getUnsignedByte(9));

            response.release();
            assertEquals(0, harness.repository().checks());

            harness.repository().exists(true);
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.run();

            assertEquals(0, harness.reply());

            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
            harness.run();

            assertEquals(1, harness.reply());
            assertTrue(harness.channel().isActive());
        }
    }

    @Test
    void mapsInfrastructureFailuresToGeneralErrorsWithoutLoggingSecretMessages() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            harness.repository().failure(new IllegalStateException(TestFixtures.CREDENTIAL));
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.run();

            assertEquals(255, harness.reply());

            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
            harness.run();

            assertEquals(255, harness.reply());
            assertTrue(logs.text().contains("account.check.failed"));
            assertTrue(logs.text().contains("account.registration.failed"));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
        }
    }

    @Test
    void closesOnUnknownPacketsAndStopsAtTheLoginBoundary() {
        for (byte[] packet : new byte[][]{
            new byte[4],
            new byte[]{0x21, 0x05, 0x0e, 0x23},
            new byte[]{0x6c, 0x05, (byte) 0xf1, 0x26}
        }) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                harness.channel().writeInbound(packet);

                if (!harness.jobs().isEmpty()) {
                    harness.run();
                }

                assertFalse(harness.channel().isActive());
                assertTrue(harness.jobs().isEmpty());
            }
        }
    }

    @Test
    void boundsPerConnectionAndGlobalWorkQueues() {
        try (var harness = new ConnectionContextTestHarness(1)) {
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertNull(harness.channel().readOutbound());
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.rejectWork(true);
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
        }
    }

    @Test
    void cancelsQueuedWorkBeforeTimeoutOrDisconnectWithoutPersistenceOrHashing() {
        for (boolean timeout : new boolean[]{true, false}) {
            try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
                harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
                Runnable dequeued = harness.jobs().element();
                harness.disconnect(timeout);

                assertTrue(harness.jobs().isEmpty());

                dequeued.run();
                dequeued.run();

                assertTrue(harness.jobs().isEmpty());
                assertEquals(0, harness.repository().checks());
                assertEquals(0, harness.repository().inserts());
                assertEquals(0, harness.hashes());
                assertNull(harness.channel().readOutbound());
                assertTrue(logs.text().contains("account.request.cancelled"));
                assertTrue(logs.text().contains("operationId="));
                assertFalse(logs.text().contains(TestFixtures.LOGIN));
                assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                assertFalse(logs.text().contains("account.registration.succeeded"));
            }
        }
    }

    @Test
    void cancelsWorkThatWasDequeuedButHasNotStarted() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
            Runnable dequeued = harness.jobs().remove();
            harness.channel().close();
            dequeued.run();

            assertEquals(0, harness.repository().checks());
            assertEquals(0, harness.hashes());
        }
    }

    @Test
    void letsStartedWorkCommitButDropsItsResponseAfterTimeoutOrDisconnect() {
        for (boolean timeout : new boolean[]{true, false}) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                harness.repository().onCheck(() -> harness.disconnect(timeout));
                harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
                harness.run();

                assertEquals(1, harness.repository().inserts());
                assertEquals(1, harness.hashes());
                assertFalse(harness.channel().isActive());
                assertNull(harness.channel().readOutbound());
            }
        }
    }

    @Test
    void keepsQueueOwnedUntilResponseWriteCompletesAndClosesOnWriteFailure() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.run();

            assertTrue(harness.jobs().isEmpty());

            delay.send();

            assertEquals(1, harness.reply());
            assertEquals(1, harness.jobs().size());

            harness.run();
            delay.fail();

            assertFalse(harness.channel().isActive());
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
            harness.run();
            harness.handler().channelRead0(harness.context(), TestFixtures.check(TestFixtures.LOGIN, 0));

            assertTrue(harness.jobs().isEmpty());

            harness.handler().exceptionCaught(harness.context(), new IOException("Write already closing"));
            delay.fail();

            assertFalse(harness.channel().isActive());
            assertEquals(1, harness.repository().inserts());
        }
    }

    @Test
    void ignoresResponseWriteCompletionDeliveredAfterDisconnect() {
        try (var logs = new LogCapture("io.netty.util.concurrent.DefaultPromise");
            var harness = new ConnectionContextTestHarness(8)) {
            var delay = new DelayedOutboundWrite();
            harness.channel().pipeline().addBefore("ConnectionContext#0", "delay", delay);
            harness.channel().writeInbound(TestFixtures.check(TestFixtures.LOGIN, 0));
            harness.run();
            harness.channel().close();
            delay.succeed();

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertEquals(0, logs.size());
        }
    }

    @Test
    void logsFrameAssemblyTimeoutOnceAndCancelsItsWaitingAccountRequest() {
        try (var logs = new LogCapture(); var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().pipeline().addFirst(new FrameDecoder(1024, 1));
            harness.channel().freezeTime();
            harness.channel().writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
            harness.channel().writeInbound(Unpooled.wrappedBuffer(new byte[]{4}));
            harness.channel().advanceTimeBy(1, TimeUnit.SECONDS);
            harness.channel().runScheduledPendingTasks();

            assertFalse(harness.channel().isActive());
            assertTrue(harness.jobs().isEmpty());
            assertTrue(logs.text().contains("server.limit.exceeded"));
            assertTrue(logs.text().contains("FRAME_ASSEMBLY_TIMEOUT"));
            assertTrue(logs.text().contains("account.request.cancelled"));
            assertFalse(logs.text().contains("protocol.frame.rejected"));
            assertFalse(logs.text().contains(TestFixtures.LOGIN));
            assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
            assertFalse(logs.text().contains(TestFixtures.VERIFIER));
        }
    }

    @Test
    void handlesIdleEventsTransportFailuresAndLateCallbacksIdempotently() throws Exception {
        for (Throwable failure : new Throwable[]{
            new MalformedPacketException("BAD"),
            new DecoderException("BAD"),
            new IOException("secret"),
            WriteTimeoutException.INSTANCE,
            new IllegalStateException("secret")
        }) {
            try (var harness = new ConnectionContextTestHarness(8)) {
                harness.handler().exceptionCaught(harness.context(), failure);

                assertFalse(harness.channel().isActive());

                harness.handler().exceptionCaught(harness.context(), failure);
                harness.handler().userEventTriggered(harness.context(), IdleStateEvent.READER_IDLE_STATE_EVENT);
                harness.handler().channelRead0(harness.context(), TestFixtures.check(TestFixtures.LOGIN, 0));
            }
        }

        try (var harness = new ConnectionContextTestHarness(8)) {
            harness.channel().pipeline().fireUserEventTriggered("OTHER_EVENT");

            assertTrue(harness.channel().isActive());

            harness.channel().advanceTimeBy(301, TimeUnit.SECONDS);
            harness.channel().runScheduledPendingTasks();

            assertFalse(harness.channel().isActive());
        }
    }

    @Test
    void dropsCompletionWhenEventLoopHasAlreadyTerminated() {
        try (var harness = new ConnectionContextTestHarness(8)) {
            var rejected = new AtomicBoolean();
            EventExecutor executor = (EventExecutor) Proxy.newProxyInstance(
                EventExecutor.class.getClassLoader(),
                new Class<?>[]{EventExecutor.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("execute") && rejected.get()) {
                        throw new RejectedExecutionException("Executor terminated");
                    }

                    return invoke(harness.context().executor(), method, arguments);
                }
            );
            var context = (ChannelHandlerContext) Proxy.newProxyInstance(
                ChannelHandlerContext.class.getClassLoader(),
                new Class<?>[]{ChannelHandlerContext.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executor")) {
                        return executor;
                    }

                    return invoke(harness.context(), method, arguments);
                }
            );
            harness.handler().channelRead0(context, TestFixtures.check(TestFixtures.LOGIN, 0));
            rejected.set(true);
            harness.run();

            assertFalse(harness.channel().isActive());
        }
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
