package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import taan.online.TestFixtures;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.RegisterAccountAction;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.FrameEncoder;

final class ConnectionContextTestHarness implements AutoCloseable {
    private final ConnectionContextTestRepository repository = new ConnectionContextTestRepository();
    private final BlockingQueue<Runnable> jobs = new ArrayBlockingQueue<>(128);
    private final ConnectionContext handler;
    private final ChannelHandlerContext context;
    private final EmbeddedChannel channel;
    private final QueuedTestExecutor workers;
    private int hashes;

    ConnectionContextTestHarness(int capacity) {
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(ConnectionContext.class),
            10,
            Duration.ofMinutes(1),
            System::nanoTime
        );
        workers = new QueuedTestExecutor(jobs);
        handler = new ConnectionContext(
            workers,
            new CheckAccountPacketHandler(new CheckAccountAction(repository)),
            new CreateAccountPacketHandler(
                new RegisterAccountAction(
                    repository,
                    credential -> {
                        hashes++;
                        return new CredentialVerifier(TestFixtures.VERIFIER);
                    }
                )
            ),
            warnings,
            capacity,
            10
        );
        channel = new EmbeddedChannel(new FrameEncoder(), handler);
        context = channel.pipeline().context(handler);
        ByteBuf greeting = channel.readOutbound();

        assertEquals(21, greeting.readIntLE());

        greeting.release();
    }

    void run() {
        jobs.remove().run();
        channel.runPendingTasks();
    }

    void disconnect(boolean timeout) {
        if (timeout) {
            channel.advanceTimeBy(11, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
        } else {
            channel.close();
        }
    }

    int reply() {
        ByteBuf response = channel.readOutbound();
        int status = response.getUnsignedByte(8);
        response.release();
        return status;
    }

    @Override
    public void close() {
        channel.finishAndReleaseAll();
    }

    ConnectionContextTestRepository repository() {
        return repository;
    }

    BlockingQueue<Runnable> jobs() {
        return jobs;
    }

    ConnectionContext handler() {
        return handler;
    }

    ChannelHandlerContext context() {
        return context;
    }

    EmbeddedChannel channel() {
        return channel;
    }

    int hashes() {
        return hashes;
    }

    void rejectWork(boolean rejectWork) {
        workers.rejectWork(rejectWork);
    }
}
