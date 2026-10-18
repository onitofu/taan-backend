package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;

class ExpelRoomParticipantPacketHandlerTest {
    @Test
    void ignoresRequestsWhenTheSelectedSessionHasLostItsRoom() {
        try (var scenario = new RoomScenario()) {
            var player = scenario.player(1, 1);
            var reply = scenario.handle(player, PqGameSessionUserExpel.TYPE_ID, RoomScenario.expel(2));

            assertEquals(RoomReplyOutcome.IGNORED, reply.outcome());
            assertTrue(scenario.lastOutput.batches.isEmpty());
        }
    }
}
