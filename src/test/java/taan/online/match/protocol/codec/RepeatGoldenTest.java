package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.ScenarioFireCommand;

class RepeatGoldenTest {
    @Test
    void readsTheCapturedPlusOneUseSpendAndTwoDelayedLaunchesWithoutChangingTheRoot() throws Exception {
        byte[] use = root("00166-s2c-PcGamePlay_UseItem.hex");
        var packet = new UseItemCodec().decode(use);
        assertEquals(1, packet.itemId());
        assertEquals(0, packet.inventoryIndex());
        assertArrayEquals(use, new UseItemCodec().encode(packet));
        byte[] spend = root("00175-s2c-PcGamePlay_SpendItem.hex");
        assertArrayEquals(spend, new SpendItemCodec().encode(new SpendItemCodec().decode(spend)));
        byte[] scene = root("00176-s2c-PcGamePlay_Scenario.hex");
        var scenario = new ScenarioCodec().decode(scene);
        assertEquals(List.of(1), scenario.scenario().selectedItems());
        var fires = scenario.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).toList();
        assertEquals(List.of(0, 1), fires.stream().map(fire -> fire.weapon().sequence()).toList());
        assertEquals(List.of(700, 1700), fires.stream().map(fire -> fire.header().tick()).toList());
        assertEquals(List.of(700, 1700), fires.stream().map(fire -> fire.weapon().shotTick()).toList());
        assertArrayEquals(scene, new ScenarioCodec().encode(scenario));
    }

    @Test
    void decodesTheHistoricalPlusTwoCombinationAsCodecEvidenceWithoutEnablingSpreadOrDamageUp() throws Exception {
        byte[] root = root("00186-s2c-PcGamePlay_Scenario.hex");
        var packet = new ScenarioCodec().decode(root);
        assertEquals(List.of(2, 3, 1050), packet.scenario().selectedItems());
        var fires = packet.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).toList();
        assertEquals(9, fires.size());
        assertEquals(List.of(700, 1700, 2700), fires.stream().map(fire -> fire.header().tick()).distinct().toList());
        assertArrayEquals(root, new ScenarioCodec().encode(packet));
    }

    private byte[] root(String name) throws Exception {
        byte[] bytes;

        try (var input = getClass().getResourceAsStream("/match/repeat/" + name)) {
            bytes = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s+", ""));
        }

        return bytes;
    }
}
