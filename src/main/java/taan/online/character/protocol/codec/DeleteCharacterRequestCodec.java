package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PqDelCharacter;
import taan.online.protocol.BinaryReader;

public final class DeleteCharacterRequestCodec {
    public PqDelCharacter decode(byte[] root) {
        var input = new BinaryReader(root, PqDelCharacter.TYPE_ID);
        var request = new PqDelCharacter(input.unsignedByte(), input.unsignedInt());
        input.finish();
        return request;
    }
}
