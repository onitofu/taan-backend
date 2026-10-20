package taan.online.match.protocol.codec;

import java.util.Optional;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

final class EatItemPayloadCodec {
    private final ScenarioCommandCodec commands = new ScenarioCommandCodec();

    EatItemPayload read(BinaryReader input) {
        int slot = input.unsignedByte();
        MatchWireChecks.slot(slot);
        var item = eat(commands.read(input));
        int flag = input.unsignedByte();
        Optional<ScenarioTeamGaugeCommand> gauge = Optional.empty();

        if (flag == 1) {
            gauge = Optional.of(gauge(commands.read(input)));
        } else if (flag != 0) {
            throw new MalformedPacketException("INVALID_EAT_GAUGE_FLAG");
        }

        input.finish();
        return new EatItemPayload(slot, item, gauge);
    }

    byte[] write(int type, EatItemPayload message) {
        MatchWireChecks.slot(message.slotIndex());
        var output = new BinaryWriter().unsignedInt(type).unsignedByte(message.slotIndex());
        commands.write(output, eat(message.command()));
        output.unsignedByte(message.gauge().isPresent() ? 1 : 0);
        message.gauge().ifPresent(value -> commands.write(output, gauge(value)));
        return output.toByteArray();
    }

    private ScenarioEatItemBoxCommand eat(ScenarioCommand command) {
        if (!(command instanceof ScenarioEatItemBoxCommand item) || item.header().kind() != 11) {
            throw new MalformedPacketException("INVALID_EAT_COMMAND");
        }

        return item;
    }

    private ScenarioTeamGaugeCommand gauge(ScenarioCommand command) {
        if (!(command instanceof ScenarioTeamGaugeCommand team) || team.header().kind() != 12) {
            throw new MalformedPacketException("INVALID_EAT_GAUGE");
        }

        return team;
    }
}
