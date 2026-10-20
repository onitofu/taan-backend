package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchRepeatTestPackets;
import taan.online.MatchSpreadTestPackets;
import taan.online.match.protocol.packet.ScenarioFireCommand;

class SpreadGoldenTest {
    @Test
    void readsNativeUseSpendAndBothFacingScenariosWithoutModifyingTheirBytes() throws Exception {
        byte[] use = MatchSpreadTestPackets.root("00236-s2c-PcGamePlay_UseItem.hex");
        var packet = new UseItemCodec().decode(use);
        assertEquals(3, packet.itemId());
        assertEquals(1, packet.inventoryIndex());
        assertArrayEquals(use, new UseItemCodec().encode(packet));
        byte[] spend = MatchSpreadTestPackets.root("00245-s2c-PcGamePlay_SpendItem.hex");
        assertArrayEquals(spend, new SpendItemCodec().encode(new SpendItemCodec().decode(spend)));

        for (String name : List.of("00246-s2c-PcGamePlay_Scenario.hex", "00283-s2c-PcGamePlay_Scenario.hex")) {
            byte[] root = MatchSpreadTestPackets.root(name);
            var scene = new ScenarioCodec().decode(root);
            assertEquals(name.startsWith("00246") ? 0 : 1, scene.facing());
            assertEquals(name.startsWith("00246") ? 0 : 2, scene.shooterSlot());
            assertEquals(List.of(3), scene.scenario().selectedItems());
            var fires = scene.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
                .map(ScenarioFireCommand.class::cast).toList();
            assertEquals(List.of(0, 1, 2), fires.stream().map(fire -> fire.weapon().sequence()).toList());
            assertEquals(List.of(700, 700, 700), fires.stream().map(fire -> fire.header().tick()).toList());
            assertArrayEquals(root, new ScenarioCodec().encode(scene));
            assertHash(root);
        }

        assertHash(use);
        assertHash(spend);
    }

    @Test
    void readsTheExistingRepeatSpreadAndDamageCaptureWithGlobalIndicesAssignedBeforeTimeSorting() throws Exception {
        byte[] root = MatchRepeatTestPackets.root("00186-s2c-PcGamePlay_Scenario.hex");
        var scene = new ScenarioCodec().decode(root);
        assertEquals(List.of(2, 3, 1050), scene.scenario().selectedItems());
        var fires = scene.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).toList();
        assertEquals(List.of(0, 3, 4, 1, 5, 6, 2, 7, 8), fires.stream().map(fire -> fire.weapon().sequence()).toList());
        assertEquals(List.of(700, 1700, 2700), fires.stream().map(fire -> fire.header().tick()).distinct().toList());
        assertArrayEquals(root, new ScenarioCodec().encode(scene));
    }

    private void assertHash(byte[] root) throws Exception {
        try (var input = getClass().getResourceAsStream("/match/spread/provenance.json")) {
            var provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
            assertTrue(provenance.contains(hash));
        }
    }
}
