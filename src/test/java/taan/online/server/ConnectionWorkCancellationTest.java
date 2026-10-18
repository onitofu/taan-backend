package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import taan.online.CharacterFixtures;
import taan.online.InventoryFixtures;
import taan.online.RoomScenario;
import taan.online.MatchFixtures;
import taan.online.match.service.MatchRegistry;
import taan.online.SelectionFixtures;
import taan.online.TestCredentialHasher;
import taan.online.TestFixtures;
import taan.online.TestRoomSelectionScheduler;
import taan.online.account.controller.CheckAccountPacketHandler;
import taan.online.account.controller.CreateAccountPacketHandler;
import taan.online.account.model.CredentialVerifier;
import taan.online.account.service.CheckAccountAction;
import taan.online.account.service.RegisterAccountAction;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.controller.DeleteCharacterPacketHandler;
import taan.online.character.repository.TestCharacterRepository;
import taan.online.character.service.ConfirmNameAction;
import taan.online.character.service.CreateCharacterAction;
import taan.online.character.service.DeleteCharacterAction;
import taan.online.character.service.ListCharactersAction;
import taan.online.inventory.repository.TestInventoryRepository;
import taan.online.logging.WarningLogLimiter;
import taan.online.network.FrameEncoder;
import taan.online.room.service.RoomRegistry;
import taan.online.session.controller.UserLoginPacketHandler;
import taan.online.session.service.LoginAction;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionCharacterAccess;
import taan.online.session.service.SessionRegistry;

class ConnectionWorkCancellationTest {
    @Test
    void removesTwelveDisconnectedRequestsFromTheRealExecutorQueueAndAcceptsNewWork() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var channels = new ArrayList<EmbeddedChannel>();
        var repository = new BlockingAccountTestRepository();
        var hashes = new AtomicInteger();

        try (var workers = workers()) {
            try {
                workers.execute(
                    () -> {
                        entered.countDown();
                        await(release);
                    }
                );

                assertTrue(entered.await(5, TimeUnit.SECONDS));

                for (int index = 0; index < 12; index++) {
                    var channel = channel(workers, repository, hashes);
                    channels.add(channel);
                    channel.writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));
                }

                assertEquals(12, workers.getQueue().size());

                for (var channel : channels) {
                    channel.close();
                    channel.close();
                }

                assertTrue(workers.getQueue().isEmpty());

                release.countDown();
                workers.submit(() -> { }).get(5, TimeUnit.SECONDS);

                assertEquals(0, hashes.get());
                assertEquals(0, repository.checks());
                assertEquals(0, repository.inserts());

                var next = channel(workers, repository, hashes);
                channels.add(next);
                next.writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 2, 0));
                workers.submit(() -> { }).get(5, TimeUnit.SECONDS);
                next.runPendingTasks();
                ByteBuf reply = next.readOutbound();

                assertEquals(0, reply.getUnsignedByte(8));

                reply.release();

                assertEquals(1, hashes.get());
                assertEquals(1, repository.inserts());
                assertFalse(next.isActive());
            } finally {
                release.countDown();
                channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            }
        }
    }

    @Test
    void doesNotInterruptStartedWorkWhenDisconnectOrTimeoutWinsAfterExecutionBegins() throws Exception {
        for (boolean timeout : new boolean[]{false, true}) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var repository = new BlockingAccountTestRepository();
            repository.onCheck(
                () -> {
                    entered.countDown();
                    await(release);
                }
            );
            var hashes = new AtomicInteger();

            try (var workers = workers()) {
                var channel = channel(workers, repository, hashes);

                try {
                    channel.writeInbound(TestFixtures.create(TestFixtures.LOGIN, TestFixtures.CREDENTIAL, 1, 0));

                    assertTrue(entered.await(5, TimeUnit.SECONDS));

                    if (timeout) {
                        channel.advanceTimeBy(11, TimeUnit.SECONDS);
                        channel.runScheduledPendingTasks();
                    } else {
                        channel.close();
                    }

                    assertFalse(channel.isActive());
                    assertEquals(0, repository.inserts());

                    release.countDown();
                    workers.submit(() -> { }).get(5, TimeUnit.SECONDS);
                    channel.runPendingTasks();

                    assertEquals(1, hashes.get());
                    assertEquals(1, repository.inserts());
                    assertNull(channel.readOutbound());
                } finally {
                    release.countDown();
                    channel.finishAndReleaseAll();
                }
            }
        }
    }

    private static ThreadPoolExecutor workers() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16));
    }

    private static EmbeddedChannel channel(
        ThreadPoolExecutor workers,
        BlockingAccountTestRepository repository,
        AtomicInteger hashes
    ) {
        var warnings = new WarningLogLimiter(
            LoggerFactory.getLogger(ConnectionWorkCancellationTest.class),
            10,
            Duration.ofMinutes(1),
            System::nanoTime
        );
        var sessions = new SessionRegistry();
        var hasher = new TestCredentialHasher(credential -> new CredentialVerifier(TestFixtures.VERIFIER));
        var characters = new TestCharacterRepository();
        var access = new SessionCharacterAccess(sessions);
        var channels = SelectionFixtures.registry(new SessionChannelAccess(sessions));
        var matches = new MatchRegistry(MatchFixtures.definitions());
        var rooms = new RoomRegistry(channels, RoomScenario.maps(), new TestRoomSelectionScheduler(), matches);
        var handler = new ConnectionContext(
            workers,
            new CheckAccountPacketHandler(new CheckAccountAction(repository)),
            new CreateAccountPacketHandler(
                new RegisterAccountAction(
                    repository,
                    new TestCredentialHasher(
                        credential -> {
                            hashes.incrementAndGet();
                            return new CredentialVerifier(TestFixtures.VERIFIER);
                        }
                    )
                )
            ),
            warnings,
            8,
            10,
            new UserLoginPacketHandler(
                new LoginAction(repository, hasher, sessions),
                sessions,
                new ListCharactersAction(characters, InventoryFixtures.catalog())
            ),
            Clock.systemUTC(),
            300,
            new ConfirmNamePacketHandler(new ConfirmNameAction(access, characters, CharacterFixtures.filter()), access),
            new CreateCharacterPacketHandler(
                new CreateCharacterAction(access, characters, CharacterFixtures.filter(), CharacterFixtures.options()),
                access
            ),
            new DeleteCharacterPacketHandler(new DeleteCharacterAction(access, characters), access),
            SelectionFixtures.dispatcher(sessions, characters),
            SelectionFixtures.lobby(sessions, channels),
            InventoryFixtures.dispatcher(sessions, new TestInventoryRepository()),
            RoomScenario.dispatcher(
                sessions,
                channels,
                characters,
                rooms
            ),
            MatchFixtures.dispatcher(rooms, matches),
            64,
            1048576,
            16384
        );
        var channel = new EmbeddedChannel(new FrameEncoder(), handler);
        ByteBuf greeting = channel.readOutbound();
        greeting.release();
        return channel;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Worker barrier timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Account operation was interrupted", failure);
        }
    }
}
