package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayEatItemBox;
import taan.online.protocol.BinaryReader;

public final class EatItemBoxCodec {
    private final EatItemPayloadCodec payload = new EatItemPayloadCodec();

    public PcGamePlayEatItemBox decode(byte[] root) {
        var message = payload.read(new BinaryReader(root, PcGamePlayEatItemBox.TYPE_ID));
        return new PcGamePlayEatItemBox(message.slotIndex(), message.command(), message.gauge());
    }

    public byte[] encode(PcGamePlayEatItemBox message) {
        return payload.write(
            PcGamePlayEatItemBox.TYPE_ID,
            new EatItemPayload(message.slotIndex(), message.command(), message.gauge())
        );
    }
}
