package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.PostmatchFixtures;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.character.protocol.packet.PcUpdateExp;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

class ExperienceResultCodecTest {
    @Test
    void matchesTheNativeExperienceSnapshotsAndAllFiveBonusRecordsWithoutRemovingVectors() throws Exception {
        for (String name : new String[]{
            "bonus-medal-soul-08-connection3-end-game.frame.hex", "negative-exp-04-connection3-end-game.frame.hex"
        }) {
            var root = Arrays.copyOfRange(
                PostmatchFixtures.bytes("native/" + name),
                4,
                PostmatchFixtures.bytes("native/" + name).length
            );
            var decoded = new EndGameCodec().decode(root);
            var slot = decoded.result().slots().getFirst();

            assertEquals(2, slot.expSnapshots().size());
            assertEquals(10, slot.expSnapshots().getFirst().level());
            assertArrayEquals(root, new EndGameCodec().encode(decoded));
        }
    }

    @Test
    void matchesTheIndependentAbsoluteUpdateAndRejectsMalformedMessages() throws Exception {
        var root = PostmatchFixtures.bytes("native/update-exp-level10.root.hex");
        var codec = new UpdateExpCodec();
        var expected = new PcUpdateExp(0, 10, 1025, 900, 1100);

        assertEquals(expected, codec.decode(root));
        assertArrayEquals(root, codec.encode(expected));
        MatchWireFixtures.malformed(root, codec::decode);
    }

    @Test
    void rejectsWrongNestedTypesAndOversizedVectors() throws Exception {
        var empty = new BinaryWriter().unsignedInt(77).unsignedInt(0).toByteArray();
        assertThrows(MalformedPacketException.class, () -> new ExpResultCodec().decode(new BinaryReader(empty, 77)));
        assertThrows(MalformedPacketException.class, () -> new ResultBonusCodec().decode(new BinaryReader(empty, 77)));
        var data = new BinaryWriter().unsignedInt(77);
        new GameResultSlotCodec().encode(
            data,
            new EndGameCodec().decode(Arrays.copyOfRange(
                PostmatchFixtures.bytes("bonus-medal-soul-08-connection3-end-game.frame.hex"),
                4,
                PostmatchFixtures.bytes("bonus-medal-soul-08-connection3-end-game.frame.hex").length
            )).result().slots().getFirst()
        );
        var bytes = data.toByteArray();
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(15, 111);
        assertThrows(
            MalformedPacketException.class,
            () -> new GameResultSlotCodec().decode(new BinaryReader(
                bytes,
                77
            ))
        );
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(15, 0).putInt(36, 6);
        assertThrows(
            MalformedPacketException.class,
            () -> new GameResultSlotCodec().decode(new BinaryReader(
                bytes,
                77
            ))
        );
    }
}
