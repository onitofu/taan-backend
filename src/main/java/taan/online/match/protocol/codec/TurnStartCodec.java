package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class TurnStartCodec {
    public byte[] encode(PcGamePlayTurnStart message) {
        MatchWireChecks.slot(message.activeSlot());
        return new BinaryWriter().unsignedInt(PcGamePlayTurnStart.TYPE_ID).unsignedByte(message.activeSlot())
            .unsignedByte(message.scalar11()).signedInt(message.windRawTenths()).unsignedShort(message.energyGrant())
            .unsignedByte(message.boxAttemptBudget()).unsignedInt(0).unsignedInt(0).unsignedInt(0)
            .unsignedInt(message.pickerBase()).unsignedInt(message.pickerTeam0()).unsignedInt(message.pickerTeam1())
            .unsignedInt(message.pickerTeam2()).unsignedShort(message.scalar50()).toByteArray();
    }

    public PcGamePlayTurnStart decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayTurnStart.TYPE_ID);
        int slot = input.unsignedByte();
        int scalar11 = input.unsignedByte();
        int wind = input.signedInt();
        int energy = input.unsignedShort();
        int budget = input.unsignedByte();

        for (int index = 0; index < 3; index++) {
            MatchWireChecks.emptyVector(input);
        }

        var message = new PcGamePlayTurnStart(
            slot,
            scalar11,
            wind,
            energy,
            budget,
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedShort()
        );
        input.finish();
        MatchWireChecks.slot(slot);
        return message;
    }
}
