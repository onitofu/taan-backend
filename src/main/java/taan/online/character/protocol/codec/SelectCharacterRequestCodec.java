package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;

public final class SelectCharacterRequestCodec {
    public PqSelectCharacter decode(byte[] root) {
        var input = new BinaryReader(root, PqSelectCharacter.TYPE_ID);

        if (input.unsignedInt() != 1) {
            throw new MalformedPacketException("UNSUPPORTED_CHARACTER_COUNT");
        }

        var request = new PqSelectCharacter(input.unsignedInt());
        input.finish();
        return request;
    }
}
