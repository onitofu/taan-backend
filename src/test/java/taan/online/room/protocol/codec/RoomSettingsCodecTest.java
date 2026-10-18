package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.room.protocol.packet.PcGameSessionChangeGameOption;

class RoomSettingsCodecTest {
    @Test
    void matchesIndependentNativeRequestsAndResearchRepliesIncludingRejectionResync() throws Exception {
        var requests = new RoomRequestCodec();
        var responses = new RoomResponseCodec();
        byte[] request = root("04-ChangeGameOption.request");
        byte[] reply = root("04-ChangeGameOption.reply");
        var decoded = requests.settings(request);

        assertEquals(2, decoded.mapId());
        assertEquals(1, decoded.gameType());
        assertEquals(0, decoded.rule());
        assertEquals(255, decoded.missionDifficultyOrIgnored());
        assertArrayEquals(RoomScenario.settings(2, 1, 0, 255), request);
        assertEquals(new PcGameSessionChangeGameOption(2, 1, 0, 0), responses.settings(reply));
        assertArrayEquals(reply, responses.encode(responses.settings(reply)));
        var rejected = root("PcChangeGameOption-rejected-current");
        assertEquals(new PcGameSessionChangeGameOption(1, 0, 2, 0), responses.settings(rejected));
        assertArrayEquals(rejected, responses.encode(responses.settings(rejected)));
        malformed(requests::settings, request);
        malformed(responses::settings, reply);
        malformed(requests::changeMap, root("PqChangeMap-map2"));
        assertEquals(2, requests.changeMap(root("PqChangeMap-map2")).mapId());
        assertArrayEquals(RoomScenario.map(2), root("PqChangeMap-map2"));
    }

    @Test
    void preservesPhysicalUnsignedFieldsBeforeApplicationValidation() {
        var requests = new RoomRequestCodec();
        var replies = new RoomResponseCodec();
        var expected = new PcGameSessionChangeGameOption(65535, 65535, 255, 255);

        assertEquals(expected, replies.settings(replies.encode(expected)));
        assertEquals(0, requests.changeMap(RoomScenario.map(0)).mapId());
        assertEquals(65535, requests.changeMap(RoomScenario.map(65535)).mapId());
        assertEquals(255, requests.settings(RoomScenario.settings(0, 65535, 255, 255)).rule());
    }
}
