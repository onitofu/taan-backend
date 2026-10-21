package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.PostmatchFixtures;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.PcResultSetReward;
import taan.online.protocol.MalformedPacketException;

class PostmatchCodecTest {
    @Test
    void encodesEachExpRecordExactlyAsTheIndependentlyCapturedClientConsumedRecord() throws Exception {
        byte[] source = PostmatchFixtures.bytes("negative-exp-04-connection3-set-reward.frame.hex");
        int labelUnits = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(16);
        int recordBytes = 17 + labelUnits * 2;
        var oracle = new ByteArrayOutputStream();
        oracle.write(source, 4, 8);

        for (int slot = 0; slot < 8; slot++) {
            oracle.write(source, 12, recordBytes);
        }

        byte[] expected = oracle.toByteArray();
        var codec = new SetRewardCodec();
        var message = codec.decode(expected);

        assertEquals(8, message.rewards().size());
        assertEquals("Research exp", message.rewards().getFirst().label());
        assertArrayEquals(expected, codec.encode(message));
        assertThrows(UnsupportedOperationException.class, () -> message.rewards().clear());
        MatchWireFixtures.malformed(expected, codec::decode);
        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.integer(expected, 4, 0)));
        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.integer(expected, 4, -1)));
        assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.integer(expected, 8, 0)));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.decode(MatchWireFixtures.octet(expected, 16 + labelUnits * 2, 4))
        );
        assertThrows(MalformedPacketException.class, () -> codec.encode(new PcResultSetReward(List.of())));

        var zero = new PcResultSetReward(Collections.nCopies(8, new GameRewardExp("", 0, 0)));

        assertEquals(zero, codec.decode(codec.encode(zero)));
    }

    @Test
    void preservesIndependentDamageAndAllThreeMedalsWithoutAddingUnimplementedBonusVectors() throws Exception {
        byte[] frame = PostmatchFixtures.bytes("bonus-medal-soul-08-connection3-end-game.frame.hex");
        byte[] root = Arrays.copyOfRange(frame, 4, frame.length);
        var codec = new EndGameCodec();
        var result = codec.decode(root);
        var first = result.result().slots().getFirst();

        assertEquals(10, first.level());
        assertEquals(543, first.damage());
        assertEquals(2, first.kills());
        assertEquals(4, first.assists());
        assertEquals(0.75f, first.accuracy());
        assertEquals(List.of(1, 2, 3), first.medals());
        assertArrayEquals(root, codec.encode(result));
        MatchWireFixtures.malformed(root, codec::decode);
    }
}
