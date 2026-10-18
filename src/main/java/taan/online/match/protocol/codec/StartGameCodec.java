package taan.online.match.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class StartGameCodec {
    public byte[] encode(PcGameSessionStartGame message) {
        validate(message);
        var output = new BinaryWriter().unsignedInt(PcGameSessionStartGame.TYPE_ID)
            .unsignedShort(message.mapId()).unsignedInt(message.players().size());

        for (var player : message.players()) {
            new GameSlotPlayInfoCodec().encode(output, player);
        }

        output.signedInt(message.initialWindRaw()).unsignedShort(message.scalar24());

        for (int leader : message.teamLeaderSlots()) {
            output.unsignedByte(leader);
        }

        return output.unsignedInt(message.scalar2c()).toByteArray();
    }

    public PcGameSessionStartGame decode(byte[] root) {
        var input = new BinaryReader(root, PcGameSessionStartGame.TYPE_ID);
        int map = input.unsignedShort();
        int count = MatchWireChecks.count(input.unsignedInt(), 8, input.remaining(), 34);
        var players = new ArrayList<GameSlotPlayInfo>();

        for (int index = 0; index < count; index++) {
            players.add(new GameSlotPlayInfoCodec().decode(input));
        }

        var message = new PcGameSessionStartGame(
            map,
            players,
            input.signedInt(),
            input.unsignedShort(),
            List.of(input.unsignedByte(), input.unsignedByte(), input.unsignedByte()),
            input.unsignedInt()
        );
        input.finish();
        validate(message);
        return message;
    }

    private void validate(PcGameSessionStartGame message) {
        MatchWireChecks.slots(message.players().stream().map(GameSlotPlayInfo::slotIndex).toList(), 1, 8);
        MatchWireChecks.triple(message.teamLeaderSlots());
    }
}
