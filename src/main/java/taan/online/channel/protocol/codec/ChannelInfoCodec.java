package taan.online.channel.protocol.codec;

import taan.online.channel.protocol.packet.ChannelInfo;
import taan.online.protocol.BinaryWriter;

public final class ChannelInfoCodec {
    public void write(BinaryWriter output, ChannelInfo info) {
        output.unsignedInt(ChannelInfo.TYPE_ID).wideString(info.name()).unsignedShort(info.channelId()).unsignedByte(0);
        endpoint(output, info.port());
        output.unsignedInt(info.fill()).unsignedInt(0).unsignedShort(info.minLevel()).unsignedShort(info.maxLevel())
            .unsignedByte(info.disabled() ? 1 : 0);
    }

    public void endpoint(BinaryWriter output, int port) {
        output.unsignedByte(127).unsignedByte(0).unsignedByte(0).unsignedByte(1).unsignedShort(port);
    }
}
