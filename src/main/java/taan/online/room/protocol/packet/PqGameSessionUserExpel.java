package taan.online.room.protocol.packet;

public record PqGameSessionUserExpel(long targetCharacterId) {
    public static final int TYPE_ID = 0x6b14091b;
}
