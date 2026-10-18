package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PcGameSessionUserExpel;

class RoomExpelCodecTest {
    @Test
    void matchesCapturedCharacterRequestAndAccountNotifications() throws Exception {
        var requests = new RoomRequestCodec();
        var replies = new RoomResponseCodec();
        var request = root("PqGameSession_UserExpel-native");

        assertEquals(1002, requests.expel(request).targetCharacterId());
        assertArrayEquals(RoomScenario.expel(1002), request);

        for (String name : new String[]{"PcGameSession_UserExpel-guest", "PcGameSession_UserExpel-local"}) {
            var reply = root(name);
            var value = replies.expel(reply);

            assertEquals("", value.text0());
            assertEquals("", value.text1());
            assertEquals(name.endsWith("guest") ? 2 : 1, value.targetAccountId());
            assertArrayEquals(reply, replies.encode(value));
            malformed(replies::expel, reply);
        }

        malformed(requests::expel, request);
    }

    @Test
    void preservesUnsignedIdsAndBothUnknownStringsWithoutAssigningThemMeaning() {
        var requests = new RoomRequestCodec();
        var replies = new RoomResponseCodec();
        var value = new PcGameSessionUserExpel("one", "два", 0xffff_ffffL);

        assertEquals(value, replies.expel(replies.encode(value)));
        assertEquals(0xffff_ffffL, requests.expel(RoomScenario.expel(0xffff_ffffL)).targetCharacterId());
        assertEquals(0, requests.expel(RoomScenario.expel(0)).targetCharacterId());
    }
}
