package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.protocol.BinaryReader;

public final class EatBonusItemCodec {
    private final EatItemPayloadCodec payload = new EatItemPayloadCodec();

    public PcGamePlayEatBonusItem decode(byte[] root) {
        var message = payload.read(new BinaryReader(root, PcGamePlayEatBonusItem.TYPE_ID));
        return new PcGamePlayEatBonusItem(message.slotIndex(), message.command(), message.gauge());
    }

    public byte[] encode(PcGamePlayEatBonusItem message) {
        return payload.write(
            PcGamePlayEatBonusItem.TYPE_ID,
            new EatItemPayload(message.slotIndex(), message.command(), message.gauge())
        );
    }
}
