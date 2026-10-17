package taan.online.channel.protocol.packet;

public record PqChannelMoveTo(int mode, int channelId, long followArgument, long clientRevision) {
    public static final int TYPE_ID = 0x33c00633;
}
