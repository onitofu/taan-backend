package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.root;
import static taan.online.server.RoomConnectionWorld.drain;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchResultAssertions;
import taan.online.RoomScenario;
import taan.online.RuntimeRoomTestAccess;
import taan.online.SelectionFixtures;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionCancelChief;
import taan.online.room.protocol.packet.PqGameSessionChallengeChief;
import taan.online.room.protocol.packet.PqGameSessionChiefDecision;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionStartGame;

class RoomCaptainConnectionTest {
    @Test
    void preservesTheRoomPhaseAndCandidateOrderingAndCancelsOnDisconnect() throws Exception {
        try (var world = new RoomConnectionWorld()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            world.request(owner, RoomScenario.settings(1, 2, 2, 255));
            drain(owner);
            drain(guest);
            world.request(owner, root("PqGameSession_ChiefDecision-native"));
            var codec = new RoomResponseCodec();
            codec.chiefDecision(read(owner));
            codec.chiefDecision(read(guest));
            world.request(owner, root("PqGameSession_ChallengeChief-native"));
            assertEquals(1001, codec.challengeChief(read(owner)).characterId());
            assertEquals(1001, codec.challengeChief(read(guest)).characterId());
            guest.channel().writeInbound(root("PqGameSession_CancelChief-native"));
            guest.disconnect(false);
            world.tick();

            assertTrue(guest.jobs().isEmpty());
            assertTrue(world.timers.tasks.isEmpty());
            drain(owner);
            world.request(owner, SelectionFixtures.cash(0, 0));
            assertArrayEquals(SelectionFixtures.cash(0, 0), read(owner));
            assertArrayEquals(SelectionFixtures.money(0), read(owner));
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
        }
    }

    @Test
    void returnsTheCorrectResultAfterSurrenderInEachCaptainExecutionProfile() throws Exception {
        for (int mode : new int[]{2, 3}) {
            try (var world = new RoomConnectionWorld(MatchExecutionMode.CLIENT_SCENARIO)) {
                var owner = world.client(1);
                var guest = world.client(2);
                world.drainOutputs();
                int room = world.create(owner);
                world.drainOutputs();
                world.join(guest, room);
                world.request(guest, RoomScenario.ready(1));
                world.request(owner, RoomScenario.settings(1, mode, mode == 2 ? 2 : 0, 255));
                world.request(owner, root("PqGameSession_ChiefDecision-native"));
                world.timers.advanceSeconds(5);
                world.tick();
                world.request(owner, root("start-empty"));
                var ownerId = RuntimeRoomTestAccess.room(world.rooms, room).ownerSessionId();
                var token = world.rooms.matchContext(ownerId).token();
                MatchFixtures.loaded(world.matches, token);

                assertEquals(
                    mode == 2 ? MatchPhase.ACTION : MatchPhase.PREVIEW,
                    world.matches.current(token).orElseThrow().phase()
                );
                world.drainOutputs();
                world.request(owner, MatchFixtures.root("give-up-empty"));

                int expectedResult = mode == 2 ? 2 : 0;

                for (var client : List.of(owner, guest)) {
                    MatchResultAssertions.readEnd(() -> read(client), expectedResult);
                    assertFalse(new RoomResponseCodec().ready(read(client)).ready());
                    assertTrue(client.channel().isActive());
                    assertNull(client.channel().readOutbound());
                }

                assertTrue(world.matches.current(token).isEmpty());
            }
        }
    }

    @Test
    void rejectsCaptainCommandsOutsideRoomAndLogsUnexpectedSchedulerFailureOnce() throws Exception {
        for (String fixture : List.of(
            "PqGameSession_ChiefDecision-native",
            "PqGameSession_ChallengeChief-native",
            "PqGameSession_CancelChief-native"
        )) {
            try (var world = new RoomConnectionWorld()) {
                var client = world.client(1);
                client.channel().writeInbound(root(fixture));
                world.tick();
                assertFalse(client.channel().isActive());
            }
        }

        try (var world = new RoomConnectionWorld(); var logs = new LogCapture()) {
            var owner = world.client(1);
            var guest = world.client(2);
            drain(owner);
            int room = world.create(owner);
            drain(guest);
            world.join(guest, room);
            drain(owner);
            world.request(guest, RoomScenario.ready(1));
            drain(owner);
            drain(guest);
            world.request(owner, RoomScenario.settings(1, 2, 2, 255));
            drain(owner);
            drain(guest);
            world.timers.failAt = 1;
            world.request(owner, root("PqGameSession_ChiefDecision-native"));
            world.tick();

            assertFalse(owner.channel().isActive());
            assertTrue(guest.channel().isActive());
            assertTrue(world.timers.tasks.isEmpty());
            assertFalse(logs.text().contains("Private scheduler value"));
            assertEquals(
                1,
                logs.events().stream().filter(event -> event.getKeyValuePairs().stream()
                .anyMatch(field -> field.key.equals("event") && field.value.equals("room.captain.failed"))).count()
            );
            drain(guest);
            assertNull(guest.channel().readOutbound());
        }

        assertEquals(RoomLogEvent.OPERATION_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionLeave.TYPE_ID));
        assertEquals(RoomLogEvent.START_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionStartGame.TYPE_ID));
        assertEquals(
            RoomLogEvent.CAPTAIN_FAILED,
            RoomRequestDispatcher.failureEvent(PqGameSessionChiefDecision.TYPE_ID)
        );
        assertEquals(
            RoomLogEvent.CAPTAIN_FAILED,
            RoomRequestDispatcher.failureEvent(PqGameSessionChallengeChief.TYPE_ID)
        );
        assertEquals(RoomLogEvent.CAPTAIN_FAILED, RoomRequestDispatcher.failureEvent(PqGameSessionCancelChief.TYPE_ID));
    }
}
