package taan.online.room.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.RoomScenario;
import taan.online.room.model.RoomSlotState;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.view.ProtocolRoomView;

class RoomSlotAvailabilityTest {
    @Test
    void mapsSelectorsAndSilentRejectionsWithoutEchoingUntrustedValues() throws Exception {
        try (var scenario = new RoomScenario()) {
            var owner = scenario.player(1, 1);
            var guest = scenario.player(2, 1);
            scenario.handle(owner, PqGameSessionCreate.TYPE_ID, root("create-unprotected"));
            var output = scenario.lastOutput;
            var type = PqGameSessionOpenCloseSlot.TYPE_ID;

            assertEquals(
                RoomReplyOutcome.SLOT_AVAILABILITY_UPDATED,
                scenario.handle(owner, type, root("PqGameSession_OpenCloseSlot-close")).outcome()
            );
            assertArrayEquals(root("PcGameSession_OpenCloseSlot-close"), output.batches.getLast().getFirst());
            assertEquals(
                RoomReplyOutcome.SLOT_AVAILABILITY_UPDATED,
                scenario.handle(owner, type, root("PqGameSession_OpenCloseSlot-open")).outcome()
            );
            assertArrayEquals(root("PcGameSession_OpenCloseSlot-open"), output.batches.getLast().getFirst());
            int sent = output.batches.size();

            for (byte[] request : new byte[][]{
                integer(root("PqGameSession_OpenCloseSlot-close"), 4, 255, 1),
                integer(root("PqGameSession_OpenCloseSlot-close"), 5, 255, 1),
                integer(root("PqGameSession_OpenCloseSlot-close"), 5, 0, 1)
            }) {
                assertEquals(
                    RoomReplyOutcome.SLOT_AVAILABILITY_REJECTED,
                    scenario.handle(owner, type, request).outcome()
                );
                assertTrue(scenario.lastOutput.batches.isEmpty());
            }

            assertEquals(sent, output.batches.size());
            assertEquals(
                RoomReplyOutcome.SLOT_AVAILABILITY_REJECTED,
                scenario.handle(guest, type, root("PqGameSession_OpenCloseSlot-close")).outcome()
            );
            scenario.channels.release(owner, "DISCONNECTED");

            assertEquals(
                RoomReplyOutcome.IGNORED,
                scenario.handle(owner, type, root("PqGameSession_OpenCloseSlot-close")).outcome()
            );
        }
    }

    @Test
    void refusesToMapAnOccupiedSlotAsAnAvailabilityNotification() {
        var view = new ProtocolRoomView(new CollectingPacketOutput());

        assertThrows(IllegalArgumentException.class, () -> view.slotAvailability(0, RoomSlotState.OCCUPIED));
        assertThrows(IllegalArgumentException.class, () -> view.slotAvailability(0, RoomSlotState.READY));
    }
}
