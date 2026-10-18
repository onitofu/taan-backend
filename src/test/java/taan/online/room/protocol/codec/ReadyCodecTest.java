package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.PcGameSessionReady;

class ReadyCodecTest {
    @Test
    void matchesIndependentReadyAndCancelCapturesUsingAccountIdentity() throws Exception {
        var requests = new RoomRequestCodec();
        var replies = new RoomResponseCodec();

        for (int index : new int[]{1, 2}) {
            boolean ready = index == 1;
            byte[] request = root("0" + index + "-Ready.request");
            byte[] reply = root("0" + index + "-Ready.reply");
            var expected = new PcGameSessionReady(2, ready);

            assertEquals(ready ? 1 : 0, requests.ready(request).ready());
            assertArrayEquals(RoomScenario.ready(ready ? 1 : 0), request);
            assertEquals(expected, replies.ready(reply));
            assertArrayEquals(reply, replies.encode(expected));
            malformed(requests::ready, request);
            malformed(replies::ready, reply);
        }

        assertEquals(255, requests.ready(RoomScenario.ready(255)).ready());
    }

    @Test
    void rejectsInvalidServerIdentitiesAndBooleans() throws Exception {
        var codec = new RoomResponseCodec();

        for (long id : new long[]{-1, 0, 0x1_0000_0000L}) {
            assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGameSessionReady(id, true)));
        }

        byte[] valid = root("01-Ready.reply");
        assertThrows(MalformedPacketException.class, () -> codec.ready(integer(valid, 4, 0, 4)));
        assertThrows(MalformedPacketException.class, () -> codec.ready(integer(valid, 8, 2, 1)));
        assertEquals(
            new PcGameSessionReady(0xffff_ffffL, false),
            codec.ready(codec.encode(new PcGameSessionReady(0xffff_ffffL, false)))
        );
    }
}
