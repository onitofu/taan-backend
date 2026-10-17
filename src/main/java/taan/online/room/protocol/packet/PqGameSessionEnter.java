package taan.online.room.protocol.packet;

public record PqGameSessionEnter(int roomId, String password, PeerMeta peerMeta) {
    public static final int TYPE_ID = 0x4902077c;

    @Override
    public String toString() {
        return "PqGameSessionEnter[REDACTED]";
    }
}
