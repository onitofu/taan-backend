package taan.online.room.protocol.packet;

public record PqGameSessionQuickJoin(int selector, PeerMeta peerMeta) {
    public static final int TYPE_ID = 0x6acf090b;
}
