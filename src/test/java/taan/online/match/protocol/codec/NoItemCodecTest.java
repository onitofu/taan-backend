package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.PcGamePlayTurnEnd;
import taan.online.protocol.BinaryWriter;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.protocol.MalformedPacketException;

class NoItemCodecTest {
    @Test
    void preservesBothRealNoItemQuizClaimsAndRejectsEveryTruncatedPrefix() throws Exception {
        var codec = new EatBonusItemCodec();
        int expected = 5;

        for (String name : List.of("noitem-01-correct", "noitem-03-repeat")) {
            byte[] root = MatchFixtures.root("no-item/" + name);
            var packet = codec.decode(root);
            var gauge = packet.gauge().orElseThrow();
            assertEquals(0, packet.command().itemId());
            assertEquals(2, packet.slotIndex());
            assertEquals(2, gauge.header().slot());
            assertEquals(4, gauge.reason());
            assertEquals(expected, gauge.newGauge());
            assertEquals(expected - 5, gauge.oldGauge());
            assertArrayEquals(root, codec.encode(packet));

            for (int size = 0; size < root.length; size++) {
                byte[] truncated = Arrays.copyOf(root, size);
                assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
            }

            expected += 5;
        }
    }

    @Test
    void readsTheDocumentedOptionalGaugeLayoutInTurnEndAndValidatesItsDescriptor() {
        byte[] root = HexFormat.of().parseHex("e206db3f0200000000013c0b65a7020c00000000000000000405000000");
        var codec = new TurnEndCodec();
        var packet = codec.decode(root);
        assertEquals(5, packet.gauge().orElseThrow().newGauge());
        assertArrayEquals(root, codec.encode(packet));

        for (int size = 0; size < root.length; size++) {
            byte[] truncated = Arrays.copyOf(root, size);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        byte[] invalidFlag = root.clone();
        invalidFlag[9] = 2;
        assertThrows(MalformedPacketException.class, () -> codec.decode(invalidFlag));
        byte[] invalidKind = root.clone();
        invalidKind[15] = 11;
        assertThrows(MalformedPacketException.class, () -> codec.decode(invalidKind));
        byte[] wrongDescriptor = new BinaryWriter().unsignedInt(PcGamePlayTurnEnd.TYPE_ID)
            .unsignedByte(2).signedInt(0).unsignedByte(1).unsignedInt(0x48a3076d)
            .unsignedByte(2).unsignedByte(8).signedInt(0).signedInt(0).toByteArray();
        assertThrows(MalformedPacketException.class, () -> codec.decode(wrongDescriptor));
        assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGamePlayTurnEnd(
            2,
            0,
            Optional.of(new ScenarioTeamGaugeCommand(new ScenarioCommandHeader(2, 11, 0, 0), 4, 5, 0))
        )));
    }
}
