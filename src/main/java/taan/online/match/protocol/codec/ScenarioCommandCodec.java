package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioFloatPoint;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.match.protocol.packet.ScenarioUseBonusCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

final class ScenarioCommandCodec {
    ScenarioCommand read(BinaryReader input) {
        int type = input.signedInt();
        var header = new ScenarioCommandHeader(
            input.unsignedByte(),
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt()
        );
        ScenarioCommand command = switch (type) {
            case ScenarioBaseCommand.TYPE_ID -> new ScenarioBaseCommand(header);
            case ScenarioWeaponCommand.TYPE_ID -> new ScenarioWeaponCommand(header, weapon(input));
            case ScenarioFireCommand.TYPE_ID -> new ScenarioFireCommand(
                header,
                weapon(input),
                input.unsignedShort(),
                input.unsignedByte(),
                floats(input),
                floats(input),
                input.unsignedByte(),
                input.unsignedShort()
            );
            case ScenarioGuidedCommand.TYPE_ID -> new ScenarioGuidedCommand(header, weapon(input), point(input));
            case ScenarioNormalCommand.TYPE_ID -> new ScenarioNormalCommand(
                header,
                input.signedInt(),
                input.signedInt()
            );
            case ScenarioDamageCommand.TYPE_ID -> new ScenarioDamageCommand(
                header,
                input.signedInt(),
                input.signedInt(),
                input.unsignedShort(),
                input.unsignedShort()
            );
            case ScenarioMakeItemBoxCommand.TYPE_ID -> new ScenarioMakeItemBoxCommand(
                header,
                input.signedLong(),
                point(input)
            );
            case ScenarioEatItemBoxCommand.TYPE_ID -> new ScenarioEatItemBoxCommand(
                header,
                input.signedLong(),
                input.unsignedShort(),
                input.signedInt()
            );
            case ScenarioTeamGaugeCommand.TYPE_ID -> new ScenarioTeamGaugeCommand(
                header,
                input.unsignedByte(),
                input.unsignedShort(),
                input.unsignedShort()
            );
            case ScenarioUseBonusCommand.TYPE_ID -> new ScenarioUseBonusCommand(header, input.unsignedShort());
            default -> throw new MalformedPacketException("UNKNOWN_SCENARIO_DESCRIPTOR");
        };
        validate(command);
        return command;
    }

    void write(BinaryWriter output, ScenarioCommand command) {
        validate(command);
        var header = command.header();
        output.signedInt(type(command)).unsignedByte(header.slot()).unsignedByte(header.kind())
            .signedInt(header.tick()).signedInt(header.scalar10());

        switch (command) {
            case ScenarioBaseCommand ignored -> { }
            case ScenarioWeaponCommand value -> weapon(output, value.weapon());
            case ScenarioFireCommand value -> {
                weapon(output, value.weapon());
                output.unsignedShort(value.weaponId()).unsignedByte(value.attackKind());
                floats(output, value.velocity());
                floats(output, value.acceleration());
                output.unsignedByte(value.byte40()).unsignedShort(value.word42());
            }
            case ScenarioGuidedCommand value -> {
                weapon(output, value.weapon());
                point(output, value.target());
            }
            case ScenarioNormalCommand value -> output.signedInt(value.scalar14()).signedInt(value.scalar18());
            case ScenarioDamageCommand value -> output.signedInt(value.damage()).signedInt(value.simulationHp())
                .unsignedShort(value.energy1c()).unsignedShort(value.energy1e());
            case ScenarioMakeItemBoxCommand value -> {
                output.signedLong(value.entityId());
                point(output, value.position());
            }
            case ScenarioEatItemBoxCommand value -> output.signedLong(value.entityId()).unsignedShort(value.itemId())
                .signedInt(value.opaque24());
            case ScenarioTeamGaugeCommand value -> output.unsignedByte(value.reason()).unsignedShort(value.newGauge())
                .unsignedShort(value.oldGauge());
            case ScenarioUseBonusCommand value -> output.unsignedShort(value.itemId());
        }
    }

    private int type(ScenarioCommand command) {
        return switch (command) {
            case ScenarioBaseCommand ignored -> ScenarioBaseCommand.TYPE_ID;
            case ScenarioWeaponCommand ignored -> ScenarioWeaponCommand.TYPE_ID;
            case ScenarioFireCommand ignored -> ScenarioFireCommand.TYPE_ID;
            case ScenarioGuidedCommand ignored -> ScenarioGuidedCommand.TYPE_ID;
            case ScenarioNormalCommand ignored -> ScenarioNormalCommand.TYPE_ID;
            case ScenarioDamageCommand ignored -> ScenarioDamageCommand.TYPE_ID;
            case ScenarioMakeItemBoxCommand ignored -> ScenarioMakeItemBoxCommand.TYPE_ID;
            case ScenarioEatItemBoxCommand ignored -> ScenarioEatItemBoxCommand.TYPE_ID;
            case ScenarioTeamGaugeCommand ignored -> ScenarioTeamGaugeCommand.TYPE_ID;
            case ScenarioUseBonusCommand ignored -> ScenarioUseBonusCommand.TYPE_ID;
        };
    }

    private void validate(ScenarioCommand command) {
        var header = command.header();
        ScenarioWireChecks.slot(header.slot());
        boolean compatible = switch (header.kind()) {
            case 0 -> command instanceof ScenarioFireCommand;
            case 1, 2, 3 -> weaponFields(command) != null;
            case 4 -> command instanceof ScenarioDamageCommand;
            case 5, 7, 8 -> true;
            case 6, 9, 14, 16 -> command instanceof ScenarioNormalCommand;
            case 10 -> command instanceof ScenarioMakeItemBoxCommand;
            case 11 -> command instanceof ScenarioEatItemBoxCommand;
            case 12 -> command instanceof ScenarioTeamGaugeCommand;
            case 13 -> command instanceof ScenarioGuidedCommand;
            case 15 -> command instanceof ScenarioUseBonusCommand;
            default -> false;
        };

        if (!compatible) {
            throw new MalformedPacketException("INCOMPATIBLE_SCENARIO_DESCRIPTOR");
        }

        if (header.kind() >= 1 && header.kind() <= 3) {
            ScenarioWireChecks.finite(Float.intBitsToFloat(weaponFields(command).scalar24Bits()));
        }

        if (command instanceof ScenarioFireCommand fire) {
            finite(fire.velocity());
            finite(fire.acceleration());
        }
    }

    private ScenarioWeaponFields weaponFields(ScenarioCommand command) {
        return switch (command) {
            case ScenarioWeaponCommand value -> value.weapon();
            case ScenarioFireCommand value -> value.weapon();
            case ScenarioGuidedCommand value -> value.weapon();
            default -> null;
        };
    }

    private ScenarioWeaponFields weapon(BinaryReader input) {
        return new ScenarioWeaponFields(
            input.unsignedByte(),
            input.unsignedByte(),
            point(input),
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte()
        );
    }

    private void weapon(BinaryWriter output, ScenarioWeaponFields value) {
        output.unsignedByte(value.sequence()).unsignedByte(value.variant());
        point(output, value.position());
        output.signedInt(value.shotTick()).signedInt(value.scalar24Bits()).unsignedByte(value.scalar28());
    }

    private ScenarioIntPoint point(BinaryReader input) {
        return new ScenarioIntPoint(input.signedInt(), input.signedInt());
    }

    private void point(BinaryWriter output, ScenarioIntPoint value) {
        output.signedInt(value.x()).signedInt(value.y());
    }

    private ScenarioFloatPoint floats(BinaryReader input) {
        return new ScenarioFloatPoint(input.float32(), input.float32());
    }

    private void floats(BinaryWriter output, ScenarioFloatPoint value) {
        output.float32(value.x()).float32(value.y());
    }

    private void finite(ScenarioFloatPoint value) {
        ScenarioWireChecks.finite(value.x());
        ScenarioWireChecks.finite(value.y());
    }
}
