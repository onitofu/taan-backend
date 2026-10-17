package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import taan.online.account.model.AccountGender;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import taan.online.TestFixtures;
import taan.online.SelectionFixtures;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.controller.DeleteCharacterPacketHandler;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.ConfirmNameAction;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.ListCharactersAction;
import taan.online.CharacterFixtures;
import taan.online.session.service.SessionCharacterAccess;
import taan.online.TestCredentialHasher;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.RegisterAccountAction;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.FrameEncoder;
import java.time.Clock;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.service.LoginAction;
import taan.online.session.service.SessionRegistry;

final class ConnectionContextTestHarness implements AutoCloseable {
    private final ConnectionContextTestRepository repository = new ConnectionContextTestRepository();
    private final BlockingQueue<Runnable> jobs = new ArrayBlockingQueue<>(128);
    private final ConnectionContext handler;
    private final ChannelHandlerContext context;
    private final EmbeddedChannel channel;
    private final QueuedTestExecutor workers;
    private final UserLoginPacketHandler login;
    private int hashes;
    private final TestCharacterRepository characters = new TestCharacterRepository();

    ConnectionContextTestHarness(int capacity) {
        this(capacity, new SessionRegistry());
    }

    ConnectionContextTestHarness(int capacity, SessionRegistry sessions) {
        this(capacity, sessions, true);
    }

    ConnectionContextTestHarness(int capacity, SessionRegistry sessions, boolean activate) {
        this(capacity, sessions, activate, Clock.systemUTC());
    }

    ConnectionContextTestHarness(int capacity, SessionRegistry sessions, boolean activate, Clock clock) {
        this(capacity, sessions, activate, clock, null);
    }

    ConnectionContextTestHarness(
        int capacity,
        SessionRegistry sessions,
        boolean activate,
        Clock clock,
        SelectedCharacterRequestDispatcher selectedOverride
    ) {
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(ConnectionContext.class),
            10,
            Duration.ofMinutes(1),
            System::nanoTime
        );
        workers = new QueuedTestExecutor(jobs);
        var hasher = new TestCredentialHasher(credential -> new CredentialVerifier(TestFixtures.VERIFIER));
        login = new UserLoginPacketHandler(
            new LoginAction(repository, hasher, sessions),
            sessions,
            new ListCharactersAction(characters)
        );
        var access = new SessionCharacterAccess(sessions);
        characters.inventoryData = SelectionFixtures.inventories(AccountGender.FEMALE);
        handler = new ConnectionContext(
            workers,
            new CheckAccountPacketHandler(new CheckAccountAction(repository)),
            new CreateAccountPacketHandler(
                new RegisterAccountAction(
                    repository,
                    new TestCredentialHasher(
                        credential -> {
                            hashes++;
                            return new CredentialVerifier(TestFixtures.VERIFIER);
                        }
                    )
                )
            ),
            warnings,
            capacity,
            10,
            login,
            clock,
            300,
            new ConfirmNamePacketHandler(new ConfirmNameAction(access, characters, CharacterFixtures.filter()), access),
            new CreateCharacterPacketHandler(
                new CreateCharacterAction(access, characters, CharacterFixtures.filter(), CharacterFixtures.options()),
                access
            ),
            new DeleteCharacterPacketHandler(new DeleteCharacterAction(access, characters), access),
            selectedOverride == null ? SelectionFixtures.dispatcher(sessions, characters) : selectedOverride
        );
        channel = new EmbeddedChannel(new FrameEncoder());
        channel.pipeline().addLast(handler);
        context = channel.pipeline().context(handler);

        if (activate) {
            handler.channelActive(context);
            ByteBuf greeting = channel.readOutbound();

            assertEquals(21, greeting.readIntLE());

            greeting.release();
        }
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

    TestCharacterRepository characters() {
        return characters;
    }

    int hashes() {
        return hashes;
    }

    void invalidateSession() {
        login.close("SESSION_INVALIDATED");
    }

    void rejectWork(boolean rejectWork) {
        workers.rejectWork(rejectWork);
    }
}
