package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.PostmatchFixtures;
import taan.online.match.protocol.packet.GameRewardGold;
import taan.online.match.protocol.packet.GameRewardQuest;
import taan.online.protocol.MalformedPacketException;

class RewardCardCodecTest {
    @Test
    void matchesNativeConsumedGoldAndEmptyQuestRecordsIncludingExtraGoldFields() throws Exception {
        for (String name : new String[]{"gold-basic-01-connection3", "gold-extra-02-connection3"}) {
            var source = PostmatchFixtures.bytes("native/" + name + "-set-reward.frame.hex");
            byte[] root = Arrays.copyOfRange(source, 4, source.length);
            var codec = new SetRewardCodec();
            var message = codec.decode(root);

            assertEquals(8, message.rewards().size());
            assertTrue(message.rewards().getFirst() instanceof GameRewardGold);
            assertArrayEquals(root, codec.encode(message));
            MatchWireFixtures.malformed(root, codec::decode);
            assertThrows(MalformedPacketException.class, () -> codec.decode(MatchWireFixtures.integer(root, 8, 0)));
            int label = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
            byte[] wrongKind = MatchWireFixtures.octet(root, 16 + label * 2, 3);

            assertThrows(MalformedPacketException.class, () -> codec.decode(wrongKind));
            var bytes = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
            int quest = -1;

            for (int offset = 8; offset < root.length - 8; offset++) {
                if (bytes.getInt(offset) == GameRewardQuest.TYPE_ID) {
                    quest = offset;
                    break;
                }
            }

            assertTrue(quest >= 0);
            byte[] wrongQuest = MatchWireFixtures.octet(root, quest + 8 + bytes.getInt(quest + 4) * 2, 4);
            assertThrows(MalformedPacketException.class, () -> codec.decode(wrongQuest));
        }
    }

    @Test
    void matchesIndependentSelectionAndOccupiedCardFrames() throws Exception {
        var codec = new ResultSelectionCodec();
        var selected = PostmatchFixtures.bytes("native/01-PrResult_SelectCard.frame.hex");
        var occupied = PostmatchFixtures.bytes("native/03-PrResult_AlreadyOpend.frame.hex");
        byte[] successRoot = Arrays.copyOfRange(selected, 4, selected.length);
        byte[] occupiedRoot = Arrays.copyOfRange(occupied, 4, occupied.length);

        assertArrayEquals(successRoot, codec.encode(codec.selected(successRoot)));
        assertArrayEquals(occupiedRoot, codec.encode(codec.alreadyOpened(occupiedRoot)));
        MatchWireFixtures.malformed(successRoot, codec::selected);
        MatchWireFixtures.malformed(occupiedRoot, codec::alreadyOpened);
        assertThrows(MalformedPacketException.class, () -> codec.selected(new byte[6]));
        assertThrows(MalformedPacketException.class, () -> codec.alreadyOpened(new byte[6]));
    }
}
