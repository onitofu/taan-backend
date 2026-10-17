package taan.online.channel.protocol.packet;

public record PrChannelMoveTo(int result, int channelId, int port, ChannelInfo info) {
    public static final int TYPE_ID = 0x33cf0634;
}
