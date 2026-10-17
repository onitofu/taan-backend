package taan.online.protocol.connection.packet;

public record PcNoticeMsg(String message) {
    public static final int TYPE_ID = 0x18dd043c;
}
