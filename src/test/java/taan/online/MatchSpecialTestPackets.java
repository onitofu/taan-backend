package taan.online;

import java.util.List;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;

public final class MatchSpecialTestPackets {
    private MatchSpecialTestPackets() {
    }

    public static PcGamePlayScenario special(PcGamePlayScenario packet) {
        var scene = packet.scenario();
        var commands = scene.commands().stream().map(MatchSpecialTestPackets::special).toList();
        return new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                commands,
                List.of(6),
                List.of(),
                1,
                scene.power(),
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            packet.scalar18(),
            0,
            packet.tail()
        );
    }

    private static ScenarioCommand special(ScenarioCommand command) {
        if (command instanceof ScenarioFireCommand fire) {
            return new ScenarioFireCommand(
                fire.header(),
                weapon(fire.weapon()),
                0,
                2,
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            );
        }
        if (command instanceof ScenarioWeaponCommand weapon) {
            return new ScenarioWeaponCommand(weapon.header(), weapon(weapon.weapon()));
        }
        return command;
    }

    private static ScenarioWeaponFields weapon(ScenarioWeaponFields value) {
        return new ScenarioWeaponFields(
            value.sequence(),
            2,
            value.position(),
            value.shotTick(),
            value.scalar24Bits(),
            value.scalar28()
        );
    }
}
