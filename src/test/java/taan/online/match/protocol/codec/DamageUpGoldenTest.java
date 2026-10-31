package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import taan.online.MatchDamageUpTestPackets;

class DamageUpGoldenTest {
    @Test
    void encodesEveryDamageCardAgainstIndependentSyntheticUseAndSpendBytes() throws Exception {
        for (int item : MatchDamageUpTestPackets.ITEMS) {
            byte[] use = MatchDamageUpTestPackets.root("use-item-" + item + "-slot0.synthetic.root.hex");
            var packet = new UseItemCodec().decode(use);
            assertEquals(item, packet.itemId());
            assertEquals(0, packet.slotIndex());
            assertEquals(0, packet.inventoryIndex());
            assertEquals(1, packet.team());
            assertArrayEquals(use, new UseItemCodec().encode(packet));

            byte[] spend = MatchDamageUpTestPackets.root("spend-item-" + item + "-slot0.synthetic.root.hex");
            var spending = new SpendItemCodec().decode(spend);
            assertEquals(1, spending.items().size());
            assertEquals(item, spending.items().getFirst().itemId());
            assertEquals(0, spending.items().getFirst().scalar2());
            assertEquals(1, spending.teamClear11());
            assertEquals(255, spending.teamClear12());
            assertArrayEquals(spend, new SpendItemCodec().encode(spending));
        }
    }

}
