package taan.online.channel.protocol.packet;

import java.util.List;

public record PrGetChannelList(String groupName, int port, List<ChannelEntry> channels) {
    public static final int TYPE_ID = 0x332c0637;

    public PrGetChannelList {
        channels = List.copyOf(channels);
    }
}
