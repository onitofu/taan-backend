package taan.online.session.protocol.packet;

public record PrUserLogin(boolean success, long accountId, int gender, int errorCode) {
    public static final int TYPE_ID = 0x1995045a;
}
