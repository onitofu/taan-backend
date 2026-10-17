package taan.online.room.protocol.packet;

public record GameSessionInfo(int roomId, int state, GameSessionDetails details) {
    public static final int TYPE_ID = 0x2e2a05ea;
}
