package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomScenario.ready;

import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.RecordingRoomOutput;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PqGameSessionReady;

class SetRoomReadyPacketHandlerTest {
    @Test
    void mapsAcceptedRejectedAndIgnoredResultsWithoutReplyingToInvalidValues() {
        try (var scenario = new RoomScenario(); var logs = new LogCapture()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            var a = new RecordingRoomOutput();
            var b = new RecordingRoomOutput();
            scenario.create.execute(owner, RoomScenario.command(), a);
            scenario.enter.execute(guest, 1, "", b);
            int type = PqGameSessionReady.TYPE_ID;

            assertEquals(RoomReplyOutcome.READY_UPDATED, scenario.handle(guest, type, ready(1)).outcome());
            assertEquals(RoomReplyOutcome.READY_UPDATED, scenario.handle(guest, type, ready(0)).outcome());
            assertEquals(RoomReplyOutcome.READY_REJECTED, scenario.handle(owner, type, ready(1)).outcome());

            for (int invalid : new int[]{2, 255}) {
                assertEquals(RoomReplyOutcome.READY_REJECTED, scenario.handle(guest, type, ready(invalid)).outcome());
                assertTrue(scenario.lastOutput.batches.isEmpty());
            }

            scenario.channels.release(guest, "DISCONNECTED");

            assertEquals(RoomReplyOutcome.IGNORED, scenario.handle(guest, type, ready(1)).outcome());
            assertTrue(logs.text().contains("INVALID_READY_VALUE"));
            assertFalse(logs.text().contains("Hero2"));
        }
    }
}
