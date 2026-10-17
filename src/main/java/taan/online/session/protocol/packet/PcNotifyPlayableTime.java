package taan.online.session.protocol.packet;

public record PcNotifyPlayableTime(int seconds) {
    public static final int TYPE_ID = 0x520407e5;
}
