package taan.online.channel.model;

import java.util.List;

public record ChannelDirectory(List<ChannelGroupDefinition> groups, int port) {
    public ChannelDirectory {
        groups = List.copyOf(groups);
        var channels = groups.stream().flatMap(group -> group.channels().stream()).toList();

        if (port < 1 || port > 65535
            || groups.stream().map(ChannelGroupDefinition::id).distinct().count() != groups.size()
            || channels.stream().map(ChannelDefinition::id).distinct().count() != channels.size()) {
            throw new IllegalArgumentException("Invalid channel directory");
        }
    }

    public List<ChannelDefinition> channels() {
        return groups.stream().flatMap(group -> group.channels().stream()).toList();
    }
}
