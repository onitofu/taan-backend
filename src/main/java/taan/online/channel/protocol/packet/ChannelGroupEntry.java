package taan.online.channel.protocol.packet;

import java.util.List;

public record ChannelGroupEntry(String name, int type, List<ChannelEntry> channels) {
    public ChannelGroupEntry {
        channels = List.copyOf(channels);
    }
}
