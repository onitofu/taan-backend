package taan.online.channel.model;

import java.util.List;

public record ChannelGroupDefinition(int id, String name, int type, List<ChannelDefinition> channels) {
    public ChannelGroupDefinition {
        channels = List.copyOf(channels);

        if (id < 1 || name.isBlank() || name.codePointCount(0, name.length()) > 64 || type < 0 || type > 6) {
            throw new IllegalArgumentException("Invalid channel group");
        }
    }
}
