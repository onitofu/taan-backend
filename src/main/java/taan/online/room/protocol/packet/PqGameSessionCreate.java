package taan.online.room.protocol.packet;

public record PqGameSessionCreate(GameSessionInfo info, PeerMeta peerMeta) {
    public static final int TYPE_ID = 0x50a707d2;

    @Override
    public String toString() {
        return "PqGameSessionCreate[REDACTED]";
    }
}
