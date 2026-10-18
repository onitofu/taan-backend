package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.room.protocol.packet.PcGameSessionCancelChief;
import taan.online.room.protocol.packet.PcGameSessionChallengeChief;
import taan.online.room.protocol.packet.PcGameSessionChiefDecision;
import taan.online.room.protocol.packet.PqGameSessionCancelChief;
import taan.online.room.protocol.packet.PqGameSessionChallengeChief;
import taan.online.room.protocol.packet.PqGameSessionChiefDecision;

class CaptainCodecTest {
    @Test
    void decodesCapturedNativeRequestsAndRejectsTruncationWrongTypesAndExtraBytes() throws Exception {
        var codec = new RoomRequestCodec();
        var begin = root("PqGameSession_ChiefDecision-native");
        var challenge = root("PqGameSession_ChallengeChief-native");
        var cancel = root("PqGameSession_CancelChief-native");

        assertEquals(new PqGameSessionChiefDecision(), codec.chiefDecision(begin));
        assertEquals(new PqGameSessionChallengeChief(1001), codec.challengeChief(challenge));
        assertEquals(new PqGameSessionCancelChief(1001), codec.cancelChief(cancel));
        malformed(codec::chiefDecision, begin);
        malformed(codec::challengeChief, challenge);
        malformed(codec::cancelChief, cancel);
    }

    @Test
    void matchesIndependentResearchResponsesAndValidatesExactResponseBoundaries() throws Exception {
        var codec = new RoomResponseCodec();
        var begin = root("PcGameSession_ChiefDecision");
        var challenge = root("PcGameSession_ChallengeChief");
        var cancel = root("PcGameSession_CancelChief");

        assertArrayEquals(begin, codec.encode(new PcGameSessionChiefDecision()));
        assertArrayEquals(challenge, codec.encode(new PcGameSessionChallengeChief(1001)));
        assertArrayEquals(cancel, codec.encode(new PcGameSessionCancelChief(1001)));
        assertEquals(new PcGameSessionChiefDecision(), codec.chiefDecision(begin));
        assertEquals(new PcGameSessionChallengeChief(1001), codec.challengeChief(challenge));
        assertEquals(new PcGameSessionCancelChief(1001), codec.cancelChief(cancel));
        malformed(codec::chiefDecision, begin);
        malformed(codec::challengeChief, challenge);
        malformed(codec::cancelChief, cancel);
    }
}
