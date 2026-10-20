package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;

class ProjectilePickupGoldenTest {
    @Test
    void decodesBothIndependentPickupRootsAndVerifiesTheirDocumentedHashes() throws Exception {
        String provenance;

        try (var input = getClass().getResourceAsStream("/match/pickup-provenance.json")) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (int slot : new int[]{0, 2}) {
            byte[] root = MatchFixtures.root("projectile-pickup-power-slot" + slot + ".synthetic");
            var packet = new ScenarioCodec().decode(root);
            var pickup = packet.scenario().commands().stream().filter(ScenarioEatItemBoxCommand.class::isInstance)
                .map(ScenarioEatItemBoxCommand.class::cast).findFirst().orElseThrow();

            assertEquals(slot, packet.shooterSlot());
            assertEquals(slot, pickup.header().slot());
            assertEquals(11, pickup.header().kind());
            assertEquals(slot == 0 ? 785 : 851, pickup.header().tick());
            assertEquals(5, pickup.itemId());
            assertEquals(225, pickup.opaque24());
            assertEquals(slot == 0 ? 0x0500000000000001L : 0x0502000000000001L, pickup.entityId());
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
            assertTrue(provenance.contains(hash));
            assertArrayEquals(root, new ScenarioCodec().encode(packet));
        }
    }
}
