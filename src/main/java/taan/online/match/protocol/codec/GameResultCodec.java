package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class GameResultCodec {
    public void encode(BinaryWriter output, GameResult result) {
        MatchWireChecks.resultSlots(result.slots().stream().map(GameResultSlot::slotIndex).toList());
        output.unsignedInt(GameResult.TYPE_ID).unsignedByte(result.resultKind()).unsignedInt(8);

        for (var slot : result.slots()) {
            new GameResultSlotCodec().encode(output, slot);
        }

        output.unsignedInt(result.scalar18()).unsignedInt(result.scalar1c());
    }

    public GameResult decode(BinaryReader input) {
        if (input.unsignedInt() != GameResult.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_MATCH_RESULT_TYPE");
        }

        int kind = input.unsignedByte();

        if (input.unsignedInt() != 8) {
            throw new MalformedPacketException("INVALID_MATCH_RESULT_COUNT");
        }

        var slots = new ArrayList<GameResultSlot>();

        for (int index = 0; index < 8; index++) {
            slots.add(new GameResultSlotCodec().decode(input));
        }

        MatchWireChecks.resultSlots(slots.stream().map(GameResultSlot::slotIndex).toList());
        return new GameResult(kind, slots, input.unsignedInt(), input.unsignedInt());
    }
}
