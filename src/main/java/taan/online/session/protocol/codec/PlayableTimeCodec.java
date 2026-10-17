package taan.online.session.protocol.codec;

import taan.online.protocol.BinaryWriter;
import taan.online.session.protocol.packet.PcNotifyPlayableTime;

public final class PlayableTimeCodec {
    public byte[] encode(PcNotifyPlayableTime packet) {
        return new BinaryWriter().unsignedInt(PcNotifyPlayableTime.TYPE_ID).unsignedByte(0).unsignedInt(0).unsignedInt(
            0
        )
            .unsignedInt(packet.seconds()).unsignedInt(packet.seconds()).unsignedInt(0x3f800000L).toByteArray();
    }
}
