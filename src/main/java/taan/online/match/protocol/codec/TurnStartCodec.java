package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.GhostMove;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class TurnStartCodec {
    public byte[] encode(PcGamePlayTurnStart message) {
        MatchWireChecks.slot(message.activeSlot());
        MatchWireChecks.slots(message.ghostMoves().stream().map(GhostMove::slotIndex).toList(), 0, 8);
        var output = new BinaryWriter().unsignedInt(PcGamePlayTurnStart.TYPE_ID).unsignedByte(message.activeSlot())
            .unsignedByte(message.scalar11()).signedInt(message.windRawTenths()).unsignedShort(message.energyGrant())
            .unsignedByte(message.boxAttemptBudget()).unsignedInt(0).unsignedInt(message.ghostMoves().size());
        message.ghostMoves().forEach(move -> output.unsignedInt(GhostMove.TYPE_ID).unsignedByte(move.slotIndex())
            .signedInt(move.startX()).signedInt(move.startY()).signedInt(move.targetX()).signedInt(move.targetY()));
        return output.unsignedInt(0).unsignedInt(message.pickerBase()).unsignedInt(message.pickerTeam0())
            .unsignedInt(message.pickerTeam1()).unsignedInt(message.pickerTeam2()).unsignedShort(message.scalar50())
            .toByteArray();
    }

    public PcGamePlayTurnStart decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayTurnStart.TYPE_ID);
        int slot = input.unsignedByte();
        int scalar11 = input.unsignedByte();
        int wind = input.signedInt();
        int energy = input.unsignedShort();
        int budget = input.unsignedByte();

        MatchWireChecks.emptyVector(input);
        int count = MatchWireChecks.count(input.unsignedInt(), 8, input.remaining(), 21);
        var moves = new ArrayList<GhostMove>();

        for (int index = 0; index < count; index++) {
            if (input.unsignedInt() != Integer.toUnsignedLong(GhostMove.TYPE_ID)) {
                throw new MalformedPacketException("UNEXPECTED_GHOST_MOVE_TYPE");
            }
            moves.add(new GhostMove(
                input.unsignedByte(),
                input.signedInt(),
                input.signedInt(),
                input.signedInt(),
                input.signedInt()
            ));
        }

        MatchWireChecks.slots(moves.stream().map(GhostMove::slotIndex).toList(), 0, 8);
        MatchWireChecks.emptyVector(input);

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
            input.unsignedShort(),
            moves
        );
        input.finish();
        MatchWireChecks.slot(slot);
        return message;
    }
}
