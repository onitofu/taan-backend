package taan.online;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;

public final class MatchScenarioTestPackets {
    private MatchScenarioTestPackets() {
    }

    public static PcGamePlayScenario shot(int slot, int facing, int damage, boolean fall) throws IOException {
        var original = new ScenarioCodec().decode(MatchFixtures.root("native-cannon-0-0"));
        var scene = original.scenario();
        var commands = new ArrayList<ScenarioCommand>();

        for (var event : scene.commands()) {
            var h = event.header();

            if (h.kind() < 10 && h.kind() != 8) {
                commands.add(withHeader(event, new ScenarioCommandHeader(slot, h.kind(), h.tick(), h.scalar10())));
            }
        }

        commands.add(new ScenarioDamageCommand(
            new ScenarioCommandHeader(slot == 0 ? 2 : 0, 4, 995, 0),
            damage,
            123456,
            0,
            0
        ));

        if (fall) {
            commands.add(new ScenarioNormalCommand(new ScenarioCommandHeader(slot, 6, 1100, 250), 685, 695));
        }

        int finish = commands.stream().mapToInt(event -> {
            var h = event.header();
            int duration = h.kind() == 1 || h.kind() == 2 || h.kind() == 3 || h.kind() == 6 ? h.scalar10() : 0;
            return h.tick() + duration;
        }).max().orElseThrow();
        commands.add(new ScenarioBaseCommand(new ScenarioCommandHeader(slot, 8, finish + 1000, 0)));
        commands.sort(Comparator.comparingLong(event -> Integer.toUnsignedLong(event.header().tick())));
        return new PcGamePlayScenario(
            facing,
            slot,
            new GameScenario(
                slot,
                scene.scalar09(),
                commands,
                scene.selectedItems(),
                scene.effects(),
                scene.flags(),
                scene.power(),
                scene.refittedSlots().stream().map(ignored -> slot).toList(),
                scene.scalar0c()
            ),
            original.scalar18(),
            original.scalar1c(),
            original.tail()
        );
    }

    private static ScenarioCommand withHeader(ScenarioCommand event, ScenarioCommandHeader header) {
        if (event instanceof ScenarioNormalCommand normal) {
            return new ScenarioNormalCommand(header, normal.scalar14(), normal.scalar18());
        }

        if (event instanceof ScenarioBaseCommand) {
            return new ScenarioBaseCommand(header);
        }

        if (event instanceof ScenarioDamageCommand damage) {
            return new ScenarioDamageCommand(
                header,
                damage.damage(),
                damage.simulationHp(),
                damage.energy1c(),
                damage.energy1e()
            );
        }

        if (event instanceof ScenarioFireCommand fire) {
            return new ScenarioFireCommand(
                header,
                fire.weapon(),
                fire.weaponId(),
                fire.attackKind(),
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            );
        }

        if (event instanceof ScenarioWeaponCommand weapon) {
            return new ScenarioWeaponCommand(header, weapon.weapon());
        }

        throw new IllegalArgumentException("Unexpected native cannon fixture event");
    }
}
