package taan.online.match.protocol.codec;

import java.util.List;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class GameResultSlotCodec {
    public void encode(BinaryWriter output, GameResultSlot slot) {
        MatchWireChecks.slot(slot.slotIndex());
        MatchWireChecks.triple(slot.scalar3c());
        output.unsignedInt(GameResultSlot.TYPE_ID).unsignedByte(slot.slotIndex()).unsignedShort(slot.scalar0a())
            .unsignedInt(slot.scalar0c()).unsignedInt(0).unsignedInt(slot.scalar1c()).unsignedByte(slot.scalar20())
            .unsignedInt(slot.scalar24()).float32(slot.scalar28()).unsignedInt(slot.scalar2c()).unsignedInt(0);

        for (int value : slot.scalar3c()) {
            output.unsignedByte(value);
        }

        output.unsignedByte(slot.scalar3f()).unsignedInt(slot.scalar40()).unsignedInt(slot.scalar44());
    }

    public GameResultSlot decode(BinaryReader input) {
        if (input.unsignedInt() != GameResultSlot.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_MATCH_RESULT_SLOT_TYPE");
        }

        int index = input.unsignedByte();
        int scalar0a = input.unsignedShort();
        long scalar0c = input.unsignedInt();
        MatchWireChecks.emptyVector(input);
        long scalar1c = input.unsignedInt();
        int scalar20 = input.unsignedByte();
        long scalar24 = input.unsignedInt();
        float scalar28 = input.float32();
        long scalar2c = input.unsignedInt();
        MatchWireChecks.emptyVector(input);
        var slot = new GameResultSlot(
            index,
            scalar0a,
            scalar0c,
            scalar1c,
            scalar20,
            scalar24,
            scalar28,
            scalar2c,
            List.of(input.unsignedByte(), input.unsignedByte(), input.unsignedByte()),
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedInt()
        );
        MatchWireChecks.slot(index);
        return slot;
    }
}
