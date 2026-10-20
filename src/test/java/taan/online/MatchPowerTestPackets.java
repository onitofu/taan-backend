package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;

public final class MatchPowerTestPackets {
    private MatchPowerTestPackets() {
    }

    public static byte[] root(String file) throws IOException {
        try (var input = MatchPowerTestPackets.class.getResourceAsStream("/match/power/" + file)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }

    public static byte[] use(int slot) throws IOException {
        return root(slot == 0 ? "00240-s2c-PcGamePlay_UseItem.hex" : "00202-s2c-PcGamePlay_UseItem.hex");
    }

    public static byte[] spend(int slot) throws IOException {
        return root(slot == 0 ? "00249-s2c-PcGamePlay_SpendItem.hex" : "00211-s2c-PcGamePlay_SpendItem.hex");
    }

    public static PcGamePlayScenario power(PcGamePlayScenario packet) {
        return attack(packet, 1);
    }

    public static PcGamePlayScenario unpowered(PcGamePlayScenario packet) {
        return attack(packet, 0);
    }

    private static PcGamePlayScenario attack(PcGamePlayScenario packet, int kind) {
        var scene = packet.scenario();
        var commands = scene.commands().stream().map(command -> attack(command, kind)).toList();
        return new PcGamePlayScenario(
            packet.facing(),
            packet.shooterSlot(),
            new GameScenario(
                scene.slot(),
                scene.scalar09(),
                commands,
                List.of(5),
                List.of(),
                0,
                scene.power(),
                scene.refittedSlots(),
                scene.scalar0c()
            ),
            packet.scalar18(),
            0,
            packet.tail()
        );
    }

    private static ScenarioCommand attack(ScenarioCommand command, int kind) {
        if (command instanceof ScenarioFireCommand fire) {
            return new ScenarioFireCommand(
                fire.header(),
                weapon(fire.weapon(), kind),
                0,
                kind,
                fire.velocity(),
                fire.acceleration(),
                fire.byte40(),
                fire.word42()
            );
        }

        if (command instanceof ScenarioWeaponCommand weapon) {
            return new ScenarioWeaponCommand(weapon.header(), weapon(weapon.weapon(), kind));
        }

        if (command instanceof ScenarioNormalCommand normal && normal.header().kind() == 9) {
            return new ScenarioNormalCommand(normal.header(), 9 + kind, normal.scalar18());
        }

        return command;
    }

    private static ScenarioWeaponFields weapon(ScenarioWeaponFields value, int kind) {
        return new ScenarioWeaponFields(
            value.sequence(),
            kind,
            value.position(),
            value.shotTick(),
            value.scalar24Bits(),
            value.scalar28()
        );
    }
}
