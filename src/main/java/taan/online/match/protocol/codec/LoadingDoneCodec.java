package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.LoadingPosition;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class LoadingDoneCodec {
    public byte[] encode(PcGamePlayLoadingDone message) {
        MatchWireChecks.slots(message.positions().stream().map(LoadingPosition::slotIndex).toList(), 0, 2);
        var output = new BinaryWriter().unsignedInt(PcGamePlayLoadingDone.TYPE_ID)
            .unsignedByte(message.positions().size());

        for (var position : message.positions()) {
            output.unsignedByte(position.slotIndex()).signedInt(position.x()).signedInt(position.y());
        }

        return output.toByteArray();
    }

    public PcGamePlayLoadingDone decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayLoadingDone.TYPE_ID);
        int count = MatchWireChecks.count(input.unsignedByte(), 2, input.remaining(), 9);
        var positions = new ArrayList<LoadingPosition>();

        for (int index = 0; index < count; index++) {
            positions.add(new LoadingPosition(input.unsignedByte(), input.signedInt(), input.signedInt()));
        }

        input.finish();
        MatchWireChecks.slots(positions.stream().map(LoadingPosition::slotIndex).toList(), 0, 2);
        return new PcGamePlayLoadingDone(positions);
    }
}
