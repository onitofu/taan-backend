package taan.online.room.protocol.packet;

public record PcGameSessionUserExpel(String text0, String text1, long targetAccountId) {
    public static final int TYPE_ID = 0x69e0090d;
}
