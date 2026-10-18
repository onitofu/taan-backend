package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import taan.online.ChannelFixtures;
import taan.online.CharacterFixtures;
import taan.online.RoomEntryFixtures;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.MatchFixtures;
import taan.online.match.service.MatchRegistry;
import taan.online.SelectionFixtures;
import taan.online.TestFixtures;
import taan.online.TestRoomSelectionScheduler;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.service.ChannelRegistry;
import taan.online.room.model.RoomIdPool;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.RoomRegistry;
import taan.online.session.service.SessionChannelAccess;
import taan.online.session.service.SessionRegistry;

final class RoomConnectionWorld implements AutoCloseable {
    final SessionRegistry sessions = new SessionRegistry();
    final ChannelRegistry channels = SelectionFixtures.registry(new SessionChannelAccess(sessions));
    final TestRoomSelectionScheduler timers = new TestRoomSelectionScheduler();
    final MatchRegistry matches = new MatchRegistry(MatchFixtures.definitions());
    final RoomRegistry rooms = new RoomRegistry(
        channels,
        new RoomIdPool(),
        RoomScenario.maps(),
        timers,
        timers::nanoTime,
        new Random(1),
        matches
    );
    private final List<ConnectionContextTestHarness> clients = new ArrayList<>();

    ConnectionContextTestHarness client(long accountId) {
        return client(accountId, null);
    }

    ConnectionContextTestHarness client(long accountId, MatchRequestDispatcher matchOverride) {
        var client = new ConnectionContextTestHarness(16, sessions, channels, rooms, accountId, matches, matchOverride);
        clients.add(client);
        request(client, TestFixtures.fixture("first-packet-minimal.root.hex"));
        read(client);
        client.repository().exists(true);
        request(client, TestFixtures.login("member" + accountId + "@example.test", TestFixtures.CREDENTIAL));
        assertEquals(1, read(client)[4]);
        client.characters().stored.add(
            CharacterFixtures.character(
                1000 + accountId,
                new AccountId(accountId),
                AccountGender.FEMALE,
                "Hero" + accountId
            )
        );
        request(client, CharacterFixtures.select(1000 + accountId));
        drain(client);
        request(client, ChannelFixtures.move(1));
        drain(client);
        request(client, ChannelFixtures.enter(1));
        drain(client);
        return client;
    }

    int create(ConnectionContextTestHarness owner) throws Exception {
        request(owner, RoomFixtures.root("create-unprotected"));
        return new RoomResponseCodec().initialize(read(owner)).info().roomId();
    }

    void join(ConnectionContextTestHarness client, int room) {
        request(client, RoomEntryFixtures.enter(room));
        assertEquals(room, new RoomResponseCodec().initialize(read(client)).info().roomId());
    }

    void request(ConnectionContextTestHarness client, byte[] root) {
        client.channel().writeInbound(root);
        client.run();
        tick();
    }

    void tick() {
        clients.forEach(client -> client.channel().runPendingTasks());
    }

    static byte[] read(ConnectionContextTestHarness client) {
        ByteBuf buffer = client.channel().readOutbound();
        byte[] root = new byte[buffer.readIntLE()];
        buffer.readBytes(root);
        buffer.release();
        return root;
    }

    static void drain(ConnectionContextTestHarness client) {
        while (!client.channel().outboundMessages().isEmpty()) {
            read(client);
        }
    }

    @Override
    public void close() {
        clients.forEach(ConnectionContextTestHarness::close);
        rooms.close();
    }
}
