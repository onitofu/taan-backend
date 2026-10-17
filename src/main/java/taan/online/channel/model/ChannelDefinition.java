package taan.online.channel.model;

public record ChannelDefinition(int id, String name, boolean enabled, int minLevel, int maxLevel, int capacity) {
    public ChannelDefinition {
        if (id < 1 || id > 65534 || name.isBlank() || name.codePointCount(0, name.length()) > 64
            || minLevel < 1 || maxLevel > 109 || minLevel > maxLevel || capacity < 1) {
            throw new IllegalArgumentException("Invalid channel definition");
        }
    }
}
