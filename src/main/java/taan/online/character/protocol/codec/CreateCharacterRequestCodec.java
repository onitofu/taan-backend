package taan.online.character.protocol.codec;

import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.protocol.BinaryReader;

public final class CreateCharacterRequestCodec {
    public PqNewCharacter decode(byte[] root) {
        var input = new BinaryReader(root, PqNewCharacter.TYPE_ID);
        int slot = input.unsignedByte();
        String name = input.wideString();
        var request = new PqNewCharacter(slot, name, new AppearancePartsCodec().decode(input));
        input.finish();
        return request;
    }
}
