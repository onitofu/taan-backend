package taan.online.channel.model;

public record ChannelDefinition(
    int id,
    String name,
    boolean enabled,
    int minLevel,
    int maxLevel,
    int capacity,
    int kind
) {
    public ChannelDefinition {
        if (id < 1 || id > 65534 || name.isBlank() || name.codePointCount(0, name.length()) > 64
            || (kind != 0 && kind != 1 && kind != 4 && kind != 6)
            || minLevel < 1 || maxLevel > 109 || minLevel > maxLevel || capacity < 1) {
            throw new IllegalArgumentException("Invalid channel definition");
        }
    }
}
