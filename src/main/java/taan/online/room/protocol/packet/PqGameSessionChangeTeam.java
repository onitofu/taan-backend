package taan.online.room.protocol.packet;

public record PqGameSessionChangeTeam(int team) {
    public static final int TYPE_ID = 0x7330094b;
}
