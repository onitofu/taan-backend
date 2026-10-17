package taan.online.channel.protocol.codec;

import taan.online.channel.protocol.packet.PqGetChannelList;
import taan.online.protocol.BinaryReader;

public final class ChannelListRequestCodec {
    public PqGetChannelList decode(byte[] root) {
        new BinaryReader(root, PqGetChannelList.TYPE_ID).finish();
        return new PqGetChannelList();
    }
}
