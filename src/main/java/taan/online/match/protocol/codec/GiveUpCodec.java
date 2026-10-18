package taan.online.match.protocol.codec;

import java.util.Objects;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;

public final class GiveUpCodec {
    public byte[] encode(PcGamePlayGiveUp message) {
        Objects.requireNonNull(message);
        return new BinaryWriter().unsignedInt(PcGamePlayGiveUp.TYPE_ID).toByteArray();
    }

    public PcGamePlayGiveUp decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayGiveUp.TYPE_ID);
        input.finish();
        return new PcGamePlayGiveUp();
    }
}
