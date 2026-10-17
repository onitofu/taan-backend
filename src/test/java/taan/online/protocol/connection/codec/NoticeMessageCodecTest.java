package taan.online.protocol.connection.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.protocol.connection.packet.PcNoticeMsg;

class NoticeMessageCodecTest {
    @Test
    void matchesTheIndependentResearchNoticeAndRejectsMalformedMessages() throws Exception {
        var codec = new NoticeMessageCodec();
        byte[] bytes = root("start-unavailable-notice");
        var message = new PcNoticeMsg("Matches are not available on this local server yet.");

        assertEquals(message, codec.decode(bytes));
        assertArrayEquals(bytes, codec.encode(message));
        malformed(codec::decode, bytes);
    }
}
