package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;

class NuclearGoldenTest {
    @Test
    void readsOriginalClientMatrixPowerAndSpecialScenariosWithoutChangingTheirWireShape() throws Exception {
        try (var input = getClass().getResourceAsStream("/match/nuclear/scenarios.tsv")) {
            var rows = new String(input.readAllBytes(), StandardCharsets.US_ASCII).lines().skip(1).toList();
            assertEquals(28, rows.size());

            for (var row : rows) {
                var fields = row.split("\t", -1);
                byte[] root;

                try (var fixture = getClass().getResourceAsStream("/match/nuclear/" + fields[0])) {
                    root = HexFormat.of().parseHex(
                        new String(fixture.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s", "")
                    );
                }

                var codec = new ScenarioCodec();
                var packet = codec.decode(root);
                assertArrayEquals(root, codec.encode(packet));
                var selected = fields[5].equals("-") ? List.<Integer>of()
                    : Arrays.stream(fields[5].split(",")).map(Integer::valueOf).toList();
                assertEquals(selected, packet.scenario().selectedItems());
                var fires = packet.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
                    .map(ScenarioFireCommand.class::cast).toList();
                assertEquals(Integer.parseInt(fields[4]), fires.size());
                fires.forEach(fire -> {
                    assertEquals(Integer.parseInt(fields[2]), fire.weaponId());
                    assertEquals(Integer.parseInt(fields[3]), fire.attackKind());
                });
                int base = Integer.parseInt(fields[1]);
                int animation = (base == 0 ? 9 : base == 8 ? 6 : 12) + Integer.parseInt(fields[3]);
                var normal = packet.scenario().commands().stream().filter(ScenarioNormalCommand.class::isInstance)
                    .map(ScenarioNormalCommand.class::cast).filter(command -> command.header().kind() == 9)
                    .findFirst().orElseThrow();
                assertEquals(animation, normal.scalar14());
            }
        }
    }
}
