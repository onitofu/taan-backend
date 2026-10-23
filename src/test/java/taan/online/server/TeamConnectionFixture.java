package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import taan.online.RoomFixtures;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.match.protocol.codec.LoadingDoneCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.service.model.RoomMatchToken;

final class TeamConnectionFixture implements AutoCloseable {
    final RoomConnectionWorld world;
    final List<ConnectionContextTestHarness> clients = new ArrayList<>();
    final Map<UUID, ConnectionContextTestHarness> sessions = new HashMap<>();
    final RoomMatchToken token;

    TeamConnectionFixture(int blue, int red, boolean captain) throws Exception {
        this(blue, red, captain, 0, 3, 8, 10);
    }

    TeamConnectionFixture(int blue, int red, boolean captain, int... weapons) throws Exception {
        this(new RoomConnectionWorld(MatchExecutionMode.CLIENT_SCENARIO), blue, red, captain, weapons);
    }

    TeamConnectionFixture(
        RoomConnectionWorld world,
        int blue,
        int red,
        boolean captain,
        int... weapons
    ) throws Exception {
        this(world, blue, red, captain, null, weapons);
    }

    TeamConnectionFixture(
        RoomConnectionWorld world,
        int blue,
        int red,
        boolean captain,
        String title,
        int... weapons
    ) throws Exception {
        this.world = world;
        int room = 0;

        for (int i = 0; i < blue + red; i++) {
            var client = world.client(i + 1);
            clients.add(client);
            world.drainOutputs();

            if (i == 0) {
                room = title == null ? world.create(client) : world.create(client, title);
            } else {
                world.join(client, room);
            }

            world.request(client, RoomScenario.team(i < blue ? 1 : 2));
            world.request(client, RoomScenario.weapon(1001 + i, weapons[i % weapons.length]));
            world.drainOutputs();
        }

        var owner = clients.getFirst();
        world.request(owner, RoomScenario.settings(1, captain ? 2 : 0, 2, 0));
        clients.stream().skip(1).forEach(c -> world.request(c, RoomScenario.ready(1)));

        if (captain) {
            world.request(owner, RoomFixtures.root("PqGameSession_ChiefDecision-native"));
            world.timers.advanceSeconds(5);
        }

        world.drainOutputs();
        world.request(owner, RoomFixtures.root("start-empty"));
        var ownerId = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
        token = world.rooms.matchContext(ownerId).token();
        var snapshot = snapshot();

        for (var client : clients) {
            for (int index = 0; index < clients.size(); index++) {
                new RoomResponseCodec().slot(read(client));
            }

            var start = new StartGameCodec().decode(read(client));
            assertEquals(clients.size(), start.players().size());
        }

        for (var member : snapshot.participants()) {
            var client = clients.get((int) member.character().accountId().value() - 1);
            sessions.put(member.sessionId(), client);
            world.request(
                client,
                new LoadingDoneCodec().encode(new PcGamePlayLoadingDone(List.of(new LoadingPosition(
                    member.slotIndex(),
                    member.spawn().settledX(),
                    member.spawn().settledY()
                ))))
            );
        }

        for (var member : snapshot.participants()) {
            var client = sessions.get(member.sessionId());

            for (int index = 0; index < clients.size() - 1; index++) {
                var position = new SetPosCodec().decode(read(client));
                assertNotEquals(member.slotIndex(), position.slotIndex());
            }

            new TurnStartCodec().decode(read(client));
        }
    }

    MatchSnapshot snapshot() {
        return world.matches.current(token).orElseThrow();
    }

    @Override
    public void close() {
        world.close();
    }
}
