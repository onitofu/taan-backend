package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchPowerTestPackets;
import taan.online.match.protocol.packet.ScenarioFireCommand;

class PowerGoldenTest {
    @Test
    void readsCapturedUsesEmptySpendsAndTheUnchangedNativePowerScenario() throws Exception {
        String provenance;

        try (var input = getClass().getResourceAsStream("/match/power/provenance.json")) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (int slot : new int[]{0, 2}) {
            byte[] root = MatchPowerTestPackets.use(slot);
            var use = new UseItemCodec().decode(root);
            assertEquals(slot, use.slotIndex());
            assertEquals(5, use.itemId());
            assertEquals(0, use.inventoryIndex());
            assertEquals(slot == 0 ? 1 : 2, use.team());
            assertArrayEquals(root, new UseItemCodec().encode(use));
            assertTrue(provenance.contains(hash(root)));

            byte[] spent = MatchPowerTestPackets.spend(slot);
            var spend = new SpendItemCodec().decode(spent);
            assertEquals(slot, spend.slotIndex());
            assertTrue(spend.items().isEmpty());
            assertEquals(255, spend.teamClear12());
            assertArrayEquals(spent, new SpendItemCodec().encode(spend));
            assertTrue(provenance.contains(hash(spent)));
        }

        byte[] root = MatchPowerTestPackets.root("00220-s2c-PcGamePlay_Scenario.hex");
        var scenario = new ScenarioCodec().decode(root);
        assertEquals(List.of(5), scenario.scenario().selectedItems());
        assertEquals(0, scenario.scenario().flags() & 1);
        assertTrue(scenario.scenario().effects().isEmpty());
        var fire = scenario.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
        assertEquals(1, fire.attackKind());
        assertEquals(1, fire.weapon().variant());
        assertTrue(provenance.contains(hash(root)));
        assertArrayEquals(root, new ScenarioCodec().encode(scenario));
    }

    @Test
    void loadsTheDistinctNativePowerCraterWithItsOwnAnchorAndChecksum() throws Exception {
        var definitions = MatchFixtures.definitions();
        var mask = definitions.missiles().get(1);
        assertEquals(128, mask.centerX());
        assertEquals(153, mask.centerY());
        assertEquals(256, mask.mask().width());
        assertEquals(256, mask.mask().height());
        assertFalse(hash(mask.mask().cells()).equals(hash(definitions.missiles().get(0).mask().cells())));

        try (var input = getClass().getResourceAsStream("/match/power-provenance.json")) {
            var provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(provenance.contains(hash(mask.mask().cells())));
        }
    }

    private String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
