package taan.online.channel.model;

import java.util.List;
import java.util.Objects;

public record ChannelDirectory(String groupName, int port, List<ChannelDefinition> channels) {
    public ChannelDirectory {
        Objects.requireNonNull(groupName);
        channels = List.copyOf(channels);

        if (groupName.isBlank() || port < 1 || port > 65535
            || channels.stream().map(ChannelDefinition::id).distinct().count() != channels.size()) {
            throw new IllegalArgumentException("Invalid channel directory");
        }
    }
}
