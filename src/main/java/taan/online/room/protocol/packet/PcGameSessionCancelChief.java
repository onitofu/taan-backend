package taan.online.room.protocol.packet;

public record PcGameSessionCancelChief(long characterId) {
    public static final int TYPE_ID = 0x7b430995;
}
