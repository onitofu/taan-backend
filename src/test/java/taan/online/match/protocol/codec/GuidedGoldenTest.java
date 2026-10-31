package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchGuidedTestPackets;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;

class GuidedGoldenTest {
    @Test
    void readsNativeActivationSpendingAndScenariosWithAndWithoutRetargeting() throws Exception {
        byte[] use = MatchGuidedTestPackets.root("00151-c2s-combat.hex");
        var activation = new UseItemCodec().decode(use);
        assertEquals(7, activation.itemId());
        assertEquals(0, activation.inventoryIndex());
        assertArrayEquals(use, new UseItemCodec().encode(activation));
        byte[] spend = MatchGuidedTestPackets.root("00155-c2s-combat.hex");
        assertArrayEquals(spend, new SpendItemCodec().encode(new SpendItemCodec().decode(spend)));

        for (String name : List.of(
            "00151-c2s-combat.hex",
            "00155-c2s-combat.hex",
            "00159-c2s-combat.hex",
            "00318-s2c-PcGamePlay_Scenario.hex",
            "00353-s2c-PcGamePlay_Scenario.hex"
        )) {
            byte[] root = MatchGuidedTestPackets.root(name);

            if (name.contains("Scenario") || name.startsWith("00159")) {
                var packet = new ScenarioCodec().decode(root);
                assertArrayEquals(root, new ScenarioCodec().encode(packet));
                assertEquals(List.of(7), packet.scenario().selectedItems());
                assertEquals(name.startsWith("00353") ? 2 : 0, packet.shooterSlot());
                var guided = packet.scenario().commands().stream()
                    .filter(event -> event.header().kind() == 13).toList();
                assertEquals(name.startsWith("00159") ? 1 : 0, guided.size());

                if (!guided.isEmpty()) {
                    var event = (ScenarioGuidedCommand) guided.getFirst();
                    assertEquals(1217, event.header().tick());
                    assertEquals(1194, event.target().x());
                    assertEquals(646, event.target().y());
                }
            }
        }
    }
}
