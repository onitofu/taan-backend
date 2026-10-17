package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.PcGameSessionOpenCloseSlot;

class RoomSlotAvailabilityCodecTest {
    @Test
    void matchesCapturedNativeRequestsAndControlReplies() throws Exception {
        var requests = new RoomRequestCodec();
        var responses = new RoomResponseCodec();

        for (int selector = 0; selector < 2; selector++) {
            String action = selector == 0 ? "close" : "open";
            byte[] request = root("PqGameSession_OpenCloseSlot-" + action);
            byte[] response = root("PcGameSession_OpenCloseSlot-" + action);
            var decoded = requests.slotAvailability(request);

            assertEquals(selector, decoded.selector());
            assertEquals(1, decoded.slotIndex());
            assertEquals(new PcGameSessionOpenCloseSlot(selector, 1), responses.slotAvailability(response));
            assertArrayEquals(response, responses.encode(new PcGameSessionOpenCloseSlot(selector, 1)));
            malformed(requests::slotAvailability, request);
            malformed(responses::slotAvailability, response);
        }
    }

    @Test
    void leavesSemanticRequestValidationToTheOwnerAndRefusesUnsafeResponses() throws Exception {
        var requests = new RoomRequestCodec();
        var responses = new RoomResponseCodec();
        byte[] request = integer(integer(root("PqGameSession_OpenCloseSlot-close"), 4, 255, 1), 5, 255, 1);

        assertEquals(255, requests.slotAvailability(request).selector());
        assertEquals(255, requests.slotAvailability(request).slotIndex());

        for (var response : new PcGameSessionOpenCloseSlot[]{
            new PcGameSessionOpenCloseSlot(-1, 0),
            new PcGameSessionOpenCloseSlot(2, 0),
            new PcGameSessionOpenCloseSlot(0, -1),
            new PcGameSessionOpenCloseSlot(0, 8)
        }) {
            assertThrows(MalformedPacketException.class, () -> responses.encode(response));
        }

        byte[] valid = root("PcGameSession_OpenCloseSlot-open");
        assertThrows(MalformedPacketException.class, () -> responses.slotAvailability(integer(valid, 4, 255, 1)));
        assertThrows(MalformedPacketException.class, () -> responses.slotAvailability(integer(valid, 5, 255, 1)));
        assertEquals(7, responses.slotAvailability(responses.encode(new PcGameSessionOpenCloseSlot(1, 7))).slotIndex());
    }
}
