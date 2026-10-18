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
import taan.online.room.protocol.packet.PcGameSessionChangeTeam;

class ChangeTeamCodecTest {
    @Test
    void matchesTheNativeMultiplayerCaptureAndKeepsArraysGrouped() throws Exception {
        var requests = new RoomRequestCodec();
        var responses = new RoomResponseCodec();

        for (int index : new int[]{5, 6}) {
            int team = index == 5 ? 1 : 2;
            byte[] request = root("0" + index + "-ChangeTeam.request");
            byte[] reply = root("0" + index + "-ChangeTeam.reply");
            var expected = new PcGameSessionChangeTeam(2, team, team == 1 ? 2 : 1, 255, team == 1 ? 1 : 2, 255);

            assertEquals(team, requests.changeTeam(request).team());
            assertArrayEquals(RoomScenario.team(team), request);
            assertEquals(expected, responses.changeTeam(reply));
            assertArrayEquals(reply, responses.encode(expected));
            malformed(requests::changeTeam, request);
            malformed(responses::changeTeam, reply);
        }

        assertEquals(255, requests.changeTeam(RoomScenario.team(255)).team());
        assertEquals(0, requests.changeTeam(RoomScenario.team(0)).team());
    }

    @Test
    void neverEncodesUnsafeOrUnsupportedNativeTransfers() throws Exception {
        var codec = new RoomResponseCodec();

        for (var value : new PcGameSessionChangeTeam[]{
            new PcGameSessionChangeTeam(0, 2, 0, 255, 2, 255),
            new PcGameSessionChangeTeam(0x1_0000_0000L, 2, 0, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 0, 0, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 3, 0, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 2, -1, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 2, 8, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 2, 0, 255, -1, 255),
            new PcGameSessionChangeTeam(2, 2, 0, 255, 8, 255),
            new PcGameSessionChangeTeam(2, 2, 0, 1, 2, 255),
            new PcGameSessionChangeTeam(2, 2, 0, 255, 2, 3),
            new PcGameSessionChangeTeam(2, 1, 0, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 2, 2, 255, 2, 255),
            new PcGameSessionChangeTeam(2, 2, 3, 255, 2, 255)
        }) {
            assertThrows(MalformedPacketException.class, () -> codec.encode(value));
        }

        byte[] valid = root("05-ChangeTeam.reply");
        assertThrows(MalformedPacketException.class, () -> codec.changeTeam(integer(valid, 10, 0, 1)));
        assertEquals(
            0xffff_ffffL,
            codec.changeTeam(codec.encode(new PcGameSessionChangeTeam(0xffff_ffffL, 2, 5, 255, 7, 255))).accountId()
        );
    }
}
