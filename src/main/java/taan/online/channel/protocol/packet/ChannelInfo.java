package taan.online.channel.protocol.packet;

public record ChannelInfo(
    String name,
    int channelId,
    int port,
    int fill,
    int minLevel,
    int maxLevel,
    boolean disabled,
    int kind
) {
    public static final int TYPE_ID = 0x18ef0445;
}
