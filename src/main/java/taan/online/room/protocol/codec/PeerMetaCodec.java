package taan.online.room.protocol.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.room.protocol.packet.PeerMeta;

public final class PeerMetaCodec {
    public PeerMeta decode(BinaryReader input) {
        return new PeerMeta(
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedByte(),
            input.unsignedInt(),
            input.unsignedInt(),
            input.unsignedInt()
        );
    }

    public void encode(BinaryWriter output, PeerMeta peer) {
        output.unsignedInt(peer.peer0()).unsignedInt(peer.peer1()).unsignedByte(peer.peer2())
            .unsignedInt(peer.peer3()).unsignedInt(peer.peer4()).unsignedInt(peer.peer5());
    }
}
