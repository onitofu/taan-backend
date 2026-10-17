package taan.online.channel.protocol.packet;

import java.util.List;

public record PrGetChannelList(List<ChannelGroupEntry> groups, int port) {
    public static final int TYPE_ID = 0x332c0637;

    public PrGetChannelList {
        groups = List.copyOf(groups);
    }
}
