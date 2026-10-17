package taan.online.channel.model;

import java.util.Objects;

public record ChannelDefinition(int id, String name) {
    public ChannelDefinition {
        Objects.requireNonNull(name);

        if (id < 1 || id > 65534 || name.isBlank()) {
            throw new IllegalArgumentException("Invalid channel definition");
        }
    }
}
