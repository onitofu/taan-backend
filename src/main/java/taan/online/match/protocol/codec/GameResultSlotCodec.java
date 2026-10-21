package taan.online.match.protocol.codec;

import java.util.List;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class GameResultSlotCodec {
    public void encode(BinaryWriter output, GameResultSlot slot) {
        MatchWireChecks.slot(slot.slotIndex());
        MatchWireChecks.triple(slot.medals());
        output.unsignedInt(GameResultSlot.TYPE_ID).unsignedByte(slot.slotIndex()).unsignedShort(slot.level())
            .unsignedInt(slot.expDeltaFallback()).unsignedInt(0).unsignedInt(slot.displayedGold())
            .unsignedByte(slot.kills()).unsignedInt(slot.damage()).float32(slot.accuracy()).unsignedInt(slot.assists())
            .unsignedInt(0);

        for (int value : slot.medals()) {
            output.unsignedByte(value);
        }

        output.unsignedByte(slot.showSoulBonus()).unsignedInt(slot.soulExp()).unsignedInt(slot.soulGold());
    }

    public GameResultSlot decode(BinaryReader input) {
        if (input.unsignedInt() != GameResultSlot.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_MATCH_RESULT_SLOT_TYPE");
        }

        int index = input.unsignedByte();
        int level = input.unsignedShort();
        long expDeltaFallback = input.unsignedInt();
        MatchWireChecks.emptyVector(input);
        long displayedGold = input.unsignedInt();
        int kills = input.unsignedByte();
        long damage = input.unsignedInt();
        float accuracy = input.float32();
        long assists = input.unsignedInt();
        MatchWireChecks.emptyVector(input);
        var slot = new GameResultSlot(
            index,
            level,
            expDeltaFallback,
            displayedGold,
            kills,
            damage,
            accuracy,
            assists,
            List.of(input.unsignedByte(), input.unsignedByte(), input.unsignedByte()),
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedInt()
        );
        MatchWireChecks.slot(index);
        return slot;
    }
}
