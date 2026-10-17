package taan.online.channel.protocol.packet;

public record ChannelEntry(int id, String name, boolean enabled, int minLevel, int maxLevel, int fill) {
}
