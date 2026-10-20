package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayEatItemBox;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.protocol.MalformedPacketException;

class EatItemBoxCodecTest {
    private final EatItemBoxCodec codec = new EatItemBoxCodec();

    @Test
    void preservesIndependentRootsAndOpaqueFieldsWithAndWithoutGauge() throws Exception {
        for (String name : new String[]{"EatItemBox-plain.synthetic", "EatItemBox-with-gauge.synthetic"}) {
            byte[] root = MatchFixtures.root(name);
            var packet = codec.decode(root);

            assertEquals(0, packet.slotIndex());
            assertEquals(123, packet.command().entityId());
            assertEquals(5, packet.command().itemId());
            assertEquals(-123, packet.command().header().tick());
            assertEquals(-456, packet.command().header().scalar10());
            assertEquals(0xf1234567, packet.command().opaque24());
            assertEquals(name.contains("with-gauge"), packet.gauge().isPresent());
            assertArrayEquals(root, codec.encode(packet));
        }
    }

    @Test
    void rejectsTruncationTrailingBytesWrongTypesAndInvalidMandatoryKinds() throws Exception {
        byte[] root = MatchFixtures.root("EatItemBox-with-gauge.synthetic");

        for (int size = 0; size < root.length; size++) {
            byte[] truncated = Arrays.copyOf(root, size);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));

        for (int offset : new int[]{0, 5, 7, 10, 33, 34, 39}) {
            byte[] wrong = root.clone();
            wrong[offset] = 5;
            assertThrows(MalformedPacketException.class, () -> codec.decode(wrong));
        }

        byte[] invalidSlot = root.clone();
        invalidSlot[4] = 8;
        assertThrows(MalformedPacketException.class, () -> codec.decode(invalidSlot));
    }

    @Test
    void validatesKindsOnEncodeInBothEatEnvelopes() throws Exception {
        var original = codec.decode(MatchFixtures.root("EatItemBox-with-gauge.synthetic"));
        var wrongItem = new ScenarioEatItemBoxCommand(new ScenarioCommandHeader(0, 5, 0, 0), 123, 5, 0);
        var wrongGauge = new ScenarioTeamGaugeCommand(new ScenarioCommandHeader(0, 5, 0, 0), 0, 0, 0);

        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(new PcGamePlayEatItemBox(0, wrongItem, Optional.empty()))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(new PcGamePlayEatItemBox(0, original.command(), Optional.of(wrongGauge)))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(new PcGamePlayEatItemBox(8, original.command(), Optional.empty()))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> new EatBonusItemCodec().encode(new PcGamePlayEatBonusItem(0, wrongItem, Optional.empty()))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> new EatBonusItemCodec().encode(
                new PcGamePlayEatBonusItem(0, original.command(), Optional.of(wrongGauge))
            )
        );
    }
}
