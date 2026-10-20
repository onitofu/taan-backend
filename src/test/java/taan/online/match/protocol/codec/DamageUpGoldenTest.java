package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.MatchDamageUpTestPackets;

class DamageUpGoldenTest {
    @Test
    void encodesEveryDamageCardAgainstIndependentSyntheticUseAndSpendBytes() throws Exception {
        String manifest;

        try (var input = getClass().getResourceAsStream("/match/damage-up/provenance.json")) {
            manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (int item : MatchDamageUpTestPackets.ITEMS) {
            byte[] use = MatchDamageUpTestPackets.root("use-item-" + item + "-slot0.synthetic.root.hex");
            var packet = new UseItemCodec().decode(use);
            assertEquals(item, packet.itemId());
            assertEquals(0, packet.slotIndex());
            assertEquals(0, packet.inventoryIndex());
            assertEquals(1, packet.team());
            assertArrayEquals(use, new UseItemCodec().encode(packet));
            assertTrue(manifest.contains(hash(use)));

            byte[] spend = MatchDamageUpTestPackets.root("spend-item-" + item + "-slot0.synthetic.root.hex");
            var spending = new SpendItemCodec().decode(spend);
            assertEquals(1, spending.items().size());
            assertEquals(item, spending.items().getFirst().itemId());
            assertEquals(0, spending.items().getFirst().scalar2());
            assertEquals(1, spending.teamClear11());
            assertEquals(255, spending.teamClear12());
            assertArrayEquals(spend, new SpendItemCodec().encode(spending));
            assertTrue(manifest.contains(hash(spend)));
        }

        try (var input = getClass().getResourceAsStream("/match/damage-up/native-damage-up-definitions.tsv")) {
            assertTrue(manifest.contains(hash(input.readAllBytes())));
        }
    }

    private String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
