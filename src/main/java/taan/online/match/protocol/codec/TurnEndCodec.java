package taan.online.match.protocol.codec;

import java.util.Optional;
import taan.online.match.protocol.packet.PcGamePlayTurnEnd;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class TurnEndCodec {
    private final ScenarioCommandCodec commands = new ScenarioCommandCodec();

    public byte[] encode(PcGamePlayTurnEnd message) {
        MatchWireChecks.slot(message.slotIndex());
        var output = new BinaryWriter().signedInt(PcGamePlayTurnEnd.TYPE_ID).unsignedByte(message.slotIndex())
            .signedInt(message.scalar14()).unsignedByte(message.gauge().isPresent() ? 1 : 0);
        message.gauge().ifPresent(value -> commands.write(output, gauge(value)));
        return output.toByteArray();
    }

    public PcGamePlayTurnEnd decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayTurnEnd.TYPE_ID);
        int slot = input.unsignedByte();
        int scalar = input.signedInt();
        MatchWireChecks.slot(slot);
        int flag = input.unsignedByte();
        Optional<ScenarioTeamGaugeCommand> gauge = Optional.empty();

        if (flag == 1) {
            gauge = Optional.of(gauge(commands.read(input)));
        } else if (flag != 0) {
            throw new MalformedPacketException("INVALID_TURN_END_FLAG");
        }

        input.finish();
        return new PcGamePlayTurnEnd(slot, scalar, gauge);
    }

    private ScenarioTeamGaugeCommand gauge(ScenarioCommand command) {
        if (!(command instanceof ScenarioTeamGaugeCommand gauge) || gauge.header().kind() != 12) {
            throw new MalformedPacketException("INVALID_TURN_END_GAUGE");
        }

        return gauge;
    }
}
