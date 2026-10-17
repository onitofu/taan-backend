package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.room.protocol.packet.PeerMeta;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PrGameSessionQuickJoinFail;

class QuickJoinCodecTest {
    @Test
    void decodesTheIndependentSelectorAndPeerTailAndRejectsOtherLayouts() throws Exception {
        var codec = new RoomRequestCodec();
        byte[] request = root("quickjoin-any");

        assertEquals(27, request.length);
        assertEquals(new PqGameSessionQuickJoin(65535, new PeerMeta(0, 0, 0, 0, 0, 0)), codec.quickJoin(request));
        byte[] changed = integer(request, 4, 7, 2);
        changed = integer(changed, 6, 0xffffffffL, 4);
        changed = integer(changed, 14, 255, 1);
        changed = integer(changed, 23, 0x12345678, 4);

        assertEquals(
            new PqGameSessionQuickJoin(7, new PeerMeta(0xffffffffL, 0, 255, 0, 0, 0x12345678)),
            codec.quickJoin(changed)
        );
        malformed(codec::quickJoin, request);
    }

    @Test
    void encodesTheIndependentEmptyFailureWithoutAddingAReasonByte() throws Exception {
        var codec = new RoomResponseCodec();
        byte[] failure = root("quickjoin-fail");

        assertEquals(4, failure.length);
        assertArrayEquals(failure, codec.encode(new PrGameSessionQuickJoinFail()));
        assertEquals(new PrGameSessionQuickJoinFail(), codec.quickJoinFail(failure));
        malformed(codec::quickJoinFail, failure);
    }
}
