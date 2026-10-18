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
import taan.online.RoomScenario;
import taan.online.SelectionFixtures;
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
            assertTrue(owner.channel().isActive());
            assertEquals(1, world.rooms.forChannel(1).getFirst().playerCount());
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
