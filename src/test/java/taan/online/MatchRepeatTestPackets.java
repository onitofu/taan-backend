package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;

public final class MatchRepeatTestPackets {
    private MatchRepeatTestPackets() {
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchRepeatTestPackets.class.getResourceAsStream("/match/repeat/" + name)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static PcGamePlayScenario shot(int slot, int facing, List<Integer> selected, int kind, int count)
        throws IOException {
        var original = MatchScenarioTestPackets.shot(slot, facing, 0, false);
        var commands = new ArrayList<ScenarioCommand>();
        var fire = original.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
        var explosion = original.scenario().commands().stream().filter(ScenarioWeaponCommand.class::isInstance)
            .map(ScenarioWeaponCommand.class::cast).filter(event -> event.header().kind() == 2)
            .findFirst().orElseThrow();

        for (int sequence = 0; sequence < count; sequence++) {
            int tick = 700 + sequence * 1000;
            commands.add(new ScenarioFireCommand(
                new ScenarioCommandHeader(slot, 0, tick, 0),
                weapon(fire.weapon(), sequence, kind, tick),
                0,
                kind,
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            ));
            commands.add(new ScenarioWeaponCommand(
                new ScenarioCommandHeader(slot, 2, tick + 500, 0),
                weapon(explosion.weapon(), sequence, kind, tick)
            ));
            commands.add(new ScenarioDamageCommand(
                new ScenarioCommandHeader(slot == 0 ? 2 : 0, 4, tick + 500, 0),
                10,
                123456,
                1,
                2
            ));
        }

        return commands(original, selected, kind, commands);
    }

    public static PcGamePlayScenario commands(
        PcGamePlayScenario original,
        List<Integer> selected,
        int kind,
        List<ScenarioCommand> events
    ) {
        var commands = new ArrayList<>(events);
        commands.removeIf(event -> event.header().kind() == 8);
        commands.sort(Comparator.comparingInt(event -> event.header().tick()));
        int finish = commands.stream().mapToInt(event -> event.header().tick()).max().orElseThrow();
        commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(original.shooterSlot(), 8, finish + 1000, 0)));
        var scene = original.scenario();
        return new PcGamePlayScenario(
            original.facing(),
            original.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                commands,
                selected,
                List.of(),
                kind == 2 ? 1 : 0,
                scene.power(),
                List.of(),
                scene.scalar0c()
            ),
            original.scalar18(),
            0,
            original.tail()
        );
    }

    private static ScenarioWeaponFields weapon(ScenarioWeaponFields original, int sequence, int kind, int tick) {
        return new ScenarioWeaponFields(
            sequence,
            kind,
            original.position(),
            tick,
            original.scalar24Bits(),
            original.scalar28()
        );
    }
}
