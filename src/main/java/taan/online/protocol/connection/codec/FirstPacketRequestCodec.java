package taan.online.protocol.connection.codec;

import taan.online.protocol.BinaryReader;
import taan.online.protocol.connection.packet.PqFirstPacket;

public final class FirstPacketRequestCodec {
    public PqFirstPacket decode(byte[] root) {
        var reader = new BinaryReader(root, PqFirstPacket.TYPE_ID);
        reader.unsignedInt();
        reader.unsignedByte();
        reader.wideString();
        reader.wideString();
        reader.optionalBuffer();

        for (int index = 0; index < 12; index++) {
            reader.unsignedInt();
        }

        reader.optionalBuffer();
        reader.unsignedInt();
        reader.unsignedInt();
        reader.wideString();
        reader.finish();
        return new PqFirstPacket();
    }
}
