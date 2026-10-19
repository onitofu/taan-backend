package taan.online.match.protocol.codec;

import java.util.Optional;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class EatBonusItemCodec {
    private final ScenarioCommandCodec commands = new ScenarioCommandCodec();

    public PcGamePlayEatBonusItem decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayEatBonusItem.TYPE_ID);
        int slot = input.unsignedByte();
        MatchWireChecks.slot(slot);
        var command = commands.read(input);

        if (!(command instanceof ScenarioEatItemBoxCommand item)) {
            throw new MalformedPacketException("INVALID_BONUS_COMMAND");
        }

        int flag = input.unsignedByte();
        Optional<ScenarioTeamGaugeCommand> gauge = Optional.empty();

        if (flag == 1) {
            var value = commands.read(input);

            if (!(value instanceof ScenarioTeamGaugeCommand team)) {
                throw new MalformedPacketException("INVALID_BONUS_GAUGE");
            }

            gauge = Optional.of(team);
        } else if (flag != 0) {
            throw new MalformedPacketException("INVALID_BONUS_GAUGE_FLAG");
        }

        input.finish();
        return new PcGamePlayEatBonusItem(slot, item, gauge);
    }

    public byte[] encode(PcGamePlayEatBonusItem message) {
        MatchWireChecks.slot(message.slotIndex());
        var output = new BinaryWriter().unsignedInt(PcGamePlayEatBonusItem.TYPE_ID).unsignedByte(message.slotIndex());
        commands.write(output, message.command());
        output.unsignedByte(message.gauge().isPresent() ? 1 : 0);
        message.gauge().ifPresent(value -> commands.write(output, value));
        return output.toByteArray();
    }
}
