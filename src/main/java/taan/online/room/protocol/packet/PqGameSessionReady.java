package taan.online.room.protocol.packet;

public record PqGameSessionReady(int ready) {
    public static final int TYPE_ID = 0x48eb0773;
}
