package taan.online.protocol.connection.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.connection.packet.PqKeepAlive;

public final class KeepAliveCodec {
    public PqKeepAlive decode(byte[] root) {
        var reader = new BinaryReader(root, PqKeepAlive.TYPE_ID);
        reader.finish();
        return new PqKeepAlive();
    }
}
