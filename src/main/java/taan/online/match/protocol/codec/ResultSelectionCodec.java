package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.match.protocol.packet.PrResultAlreadyOpend;
import taan.online.match.protocol.packet.PrResultSelectCard;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class ResultSelectionCodec {
    public byte[] encode(PrResultSelectCard message) {
        return new BinaryWriter().unsignedInt(PrResultSelectCard.TYPE_ID)
            .unsignedByte(message.cardIndex()).unsignedByte(message.slotIndex()).toByteArray();
    }

    public PrResultSelectCard selected(byte[] root) {
        var input = new BinaryReader(root, PrResultSelectCard.TYPE_ID);
        var result = new PrResultSelectCard(input.unsignedByte(), input.unsignedByte());
        input.finish();
        return result;
    }

    public byte[] encode(PrResultAlreadyOpend message) {
        return new BinaryWriter().unsignedInt(PrResultAlreadyOpend.TYPE_ID)
            .unsignedByte(message.cardIndex()).unsignedByte(message.slotIndex()).toByteArray();
    }

    public PrResultAlreadyOpend alreadyOpened(byte[] root) {
        var input = new BinaryReader(root, PrResultAlreadyOpend.TYPE_ID);
        var result = new PrResultAlreadyOpend(input.unsignedByte(), input.unsignedByte());
        input.finish();
        return result;
    }

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
