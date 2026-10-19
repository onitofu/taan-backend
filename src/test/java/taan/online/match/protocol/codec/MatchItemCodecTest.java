package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.SpentItem;
import taan.online.protocol.MalformedPacketException;

class MatchItemCodecTest {
    @Test
    void matchesIndependentNativeSpecialAndEmptySpendAndSpecialScenarioRoots() throws Exception {
        byte[] use = root("00283-c2s-combat.hex");
        byte[] spend = root("00287-c2s-combat.hex");
        var useCodec = new UseItemCodec();
        var spendCodec = new SpendItemCodec();
        assertEquals(new PcGamePlayUseItem(0, 1, 0, 0, 255, 6), useCodec.decode(use));
        assertArrayEquals(use, useCodec.encode(useCodec.decode(use)));
        assertEquals(new PcGamePlaySpendItem(0, 1, 255, List.of()), spendCodec.decode(spend));
        assertArrayEquals(spend, spendCodec.encode(spendCodec.decode(spend)));
        assertEquals(
            PcGamePlaySpendItem.TYPE_ID,
            new UnsupportedGameplayCodec().decode(PcGamePlaySpendItem.TYPE_ID, spend).typeId()
        );
        assertTrue(UnsupportedGameplayCodec.accepts(PcGamePlaySpendItem.TYPE_ID));
        var specialRoot = root("00291-c2s-combat.hex");
        var special = new ScenarioCodec().decode(specialRoot);
        assertEquals(List.of(6), special.scenario().selectedItems());
        assertEquals(1, special.scenario().flags() & 1);
        assertArrayEquals(specialRoot, new ScenarioCodec().encode(special));

        for (int size = 0; size < use.length; size++) {
            var truncated = Arrays.copyOf(use, size);
            assertThrows(MalformedPacketException.class, () -> useCodec.decode(truncated));
        }

        for (int size = 0; size < spend.length; size++) {
            var truncated = Arrays.copyOf(spend, size);
            assertThrows(MalformedPacketException.class, () -> spendCodec.decode(truncated));
        }

        assertThrows(MalformedPacketException.class, () -> useCodec.decode(Arrays.copyOf(use, 12)));
        assertThrows(MalformedPacketException.class, () -> spendCodec.decode(Arrays.copyOf(spend, 12)));
        use[4] = 8;
        spend[7] = 4;
        assertThrows(MalformedPacketException.class, () -> useCodec.decode(use));
        assertThrows(MalformedPacketException.class, () -> spendCodec.decode(spend));
        assertThrows(MalformedPacketException.class, () -> useCodec.encode(new PcGamePlayUseItem(8, 1, 0, 0, 255, 6)));
        assertThrows(
            MalformedPacketException.class,
            () -> spendCodec.encode(new PcGamePlaySpendItem(
                0,
                1,
                255,
                List.of(new SpentItem(6, 1), new SpentItem(6, 2), new SpentItem(6, 3), new SpentItem(6, 4))
            ))
        );
        var full = new PcGamePlaySpendItem(2, 255, 2, List.of(new SpentItem(65535, 255), new SpentItem(6, 0)));
        assertEquals(full, spendCodec.decode(spendCodec.encode(full)));
        assertThrows(
            MalformedPacketException.class,
            () -> spendCodec.decode(Arrays.copyOf(spendCodec.encode(full), 16))
        );
    }

    static byte[] root(String name) throws Exception {
        try (var input = MatchItemCodecTest.class.getResourceAsStream("/match/" + name)) {
            return HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s+", ""));
        }
    }
}
