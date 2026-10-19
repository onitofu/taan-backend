package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

class EatBonusItemCodecTest {
    private final EatBonusItemCodec codec = new EatBonusItemCodec();

    @Test
    void readsAndWritesBothNativeCapturesAndAnIndependentOptionalGaugeFixture() throws Exception {
        for (String name : new String[]{
            "pending-03-PcGamePlay_EatBonusItem",
            "pending-04-PcGamePlay_EatBonusItem",
            "EatBonusItem-with-gauge.synthetic"
        }) {
            byte[] root = MatchFixtures.root(name);
            var packet = codec.decode(root);
            assertEquals(-1L, packet.command().entityId());
            assertEquals(packet.slotIndex(), packet.command().header().slot());
            assertEquals(name.endsWith("synthetic"), packet.gauge().isPresent());
            assertArrayEquals(root, codec.encode(packet));
        }
    }

    @Test
    void rejectsEveryTruncationInvalidSlotsFlagsDescriptorsAndTrailingBytes() throws Exception {
        byte[] root = MatchFixtures.root("EatBonusItem-with-gauge.synthetic");
        for (int size = 0; size < root.length; size++) {
            byte[] truncated = Arrays.copyOf(root, size);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
        var packet = codec.decode(root);
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(
                new PcGamePlayEatBonusItem(8, packet.command(), packet.gauge())
            )
        );
        root[4] = 8;
        assertThrows(MalformedPacketException.class, () -> codec.decode(root));

        byte[] plain = MatchFixtures.root("pending-03-PcGamePlay_EatBonusItem");
        plain[plain.length - 1] = 2;
        assertThrows(MalformedPacketException.class, () -> codec.decode(plain));
        byte[] base = new BinaryWriter().unsignedInt(0x66e908d2).unsignedByte(0)
            .unsignedInt(0x48a3076d).unsignedByte(0).unsignedByte(5).signedInt(0).signedInt(0)
            .unsignedByte(0).toByteArray();
        assertThrows(MalformedPacketException.class, () -> codec.decode(base));

        byte[] gauge = MatchFixtures.root("EatBonusItem-with-gauge.synthetic");
        byte[] wrong = Arrays.copyOf(gauge, 34 + 14);
        System.arraycopy(base, 5, wrong, 34, 14);
        assertThrows(MalformedPacketException.class, () -> codec.decode(wrong));
    }
}
