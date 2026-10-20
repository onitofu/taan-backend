package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchLootPolicy;

public final class MatchGuidedTestPackets {
    public static final MatchLootPolicy POLICY = new MatchLootPolicy(
        Set.of(1, 2, 3, 5, 7, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050)
    );

    private MatchGuidedTestPackets() {
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchGuidedTestPackets.class.getResourceAsStream("/match/guided/" + name)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static PcGamePlayScenario shot(int slot, int facing, List<Integer> selected, int kind, int launches)
        throws IOException {
        var original = selected.contains(3)
            ? MatchSpreadTestPackets.shot(slot, facing, selected, kind, launches, .5f)
            : MatchRepeatTestPackets.shot(slot, facing, selected, kind, launches);
        var commands = new ArrayList<>(original.scenario().commands());

        for (var event : original.scenario().commands()) {
            if (event instanceof ScenarioFireCommand fire) {
                var weapon = fire.weapon();
                commands.add(new ScenarioGuidedCommand(
                    new ScenarioCommandHeader(slot, 13, fire.header().tick() + 200, 0),
                    new ScenarioWeaponFields(
                        weapon.sequence(),
                        weapon.variant(),
                        weapon.position(),
                        weapon.shotTick(),
                        0x7fc01234,
                        255
                    ),
                    new ScenarioIntPoint(1194, 646)
                ));
            }
        }

        return MatchRepeatTestPackets.commands(original, selected, kind, commands);
    }
}
