package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class ResultSelectionCodec {
    public byte[] encode(PqResultSelectCard message) {
        return new BinaryWriter().unsignedInt(PqResultSelectCard.TYPE_ID)
            .unsignedByte(message.scalar10()).unsignedByte(message.scalar11()).toByteArray();
    }

    public PqResultSelectCard selectCard(byte[] root) {
        var input = new BinaryReader(root, PqResultSelectCard.TYPE_ID);
        var message = new PqResultSelectCard(input.unsignedByte(), input.unsignedByte());
        input.finish();
        return message;
    }

    public byte[] encode(PqResultRandSelect message) {
        return new BinaryWriter().unsignedInt(PqResultRandSelect.TYPE_ID)
            .unsignedByte(message.scalar10()).toByteArray();
    }

    public PqResultRandSelect randomSelect(byte[] root) {
        var input = new BinaryReader(root, PqResultRandSelect.TYPE_ID);
        var message = new PqResultRandSelect(input.unsignedByte());
        input.finish();
        return message;
    }
}
