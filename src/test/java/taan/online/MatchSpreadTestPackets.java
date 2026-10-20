package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioFloatPoint;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchLootPolicy;

public final class MatchSpreadTestPackets {
    public static final MatchLootPolicy POLICY = new MatchLootPolicy(
        Set.of(1, 2, 3, 5, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050)
    );

    private MatchSpreadTestPackets() {
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchSpreadTestPackets.class.getResourceAsStream("/match/spread/" + name)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static PcGamePlayScenario shot(
        int slot,
        int facing,
        List<Integer> selected,
        int kind,
        int launches,
        float power
    ) throws IOException {
        var original = MatchRepeatTestPackets.shot(slot, facing, selected, kind, 1);
        var fire = original.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
        var explosion = original.scenario().commands().stream().filter(ScenarioWeaponCommand.class::isInstance)
            .map(ScenarioWeaponCommand.class::cast).findFirst().orElseThrow();
        var commands = new ArrayList<ScenarioCommand>();

        for (int sequence = 0; sequence < launches * 3; sequence++) {
            int launch = sequence < launches ? sequence : (sequence - launches) / 2;
            int branch = sequence < launches ? 0 : (sequence - launches) % 2 + 1;
            int tick = 700 + 1000 * launch;
            int sign = facing == 0 ? -1 : 1;
            int offset = branch == 0 ? 0 : branch == 1 ? sign * 4 : -sign * 4;
            float strength = branch == 0 ? power : branch == 1 ? Math.max(0, power - .03f) : power + .03f;
            double angle = Math.toRadians((facing == 0 ? 45 : 135) + offset);
            var velocity = new ScenarioFloatPoint(
                (float) (Math.cos(angle) * strength * 1000),
                (float) (Math.sin(angle) * strength * 1000)
            );
            commands.add(new ScenarioFireCommand(
                new ScenarioCommandHeader(slot, 0, tick, 0),
                weapon(fire.weapon(), sequence, kind, tick),
                0,
                kind,
                velocity,
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            ));
            commands.add(new ScenarioWeaponCommand(
                new ScenarioCommandHeader(slot, 2, tick + 1500, 0),
                weapon(explosion.weapon(), sequence, kind, tick)
            ));
            commands.add(new ScenarioDamageCommand(
                new ScenarioCommandHeader(slot == 0 ? 2 : 0, 4, tick + 1500, 0),
                10,
                123456,
                1,
                2
            ));
        }

        var scene = original.scenario();
        var base = new PcGamePlayScenario(
            facing,
            slot,
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                scene.commands(),
                selected,
                scene.effects(),
                scene.flags(),
                power,
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            original.scalar18(),
            original.scalar1c(),
            original.tail()
        );
        return MatchRepeatTestPackets.commands(base, selected, kind, commands);
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
